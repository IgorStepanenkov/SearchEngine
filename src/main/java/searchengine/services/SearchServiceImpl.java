package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.helpers.LemmaSearchResult;
import searchengine.services.helpers.SearchParamsValidationResult;
import searchengine.services.helpers.SiteLemmasSearchResult;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("JavadocLinkAsPlainText")
@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SearchServiceImpl implements SearchService {
    private static final int MAX_FREQUENCY_PERCENT = 25;  // Максимальный процент встречаемости леммы. Если
    // встречаемость выше и лемма не одна, то она не учитывается при дальнейшем поиске
    private static final int MAX_SNIPPET_LENGTH = 240;  // Максимальный размер snippet/а в символах
    private static final String EMPTY_QUERY = "Задан пустой поисковый запрос";
    private static final String INVALID_LIMIT = "Задано некорректное значение параметра limit";
    private static final String INVALID_OFFSET = "Задано некорректное значение параметра offset";
    private static final String NO_LEMMAS_IN_QUERY = "Поисковый запрос не содержит значимые ключевые слова";
    private static final String SITE_NOT_FOUND = "Заданный сайт не найден в базе данных";
    private static final String INDEXING_IN_PROCESS = "Сайты в процессе индексации";
    private static final String SITE_INDEXING_IN_PROCESS = "Сайт в процессе индексации";
    private static final String NOT_INDEXED = "Сайты не индексированы";
    private static final String SITE_NOT_INDEXED = "Сайт не индексирован";

    private final IndexingService indexingService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaAnalyzerService lemmaAnalyzer;

    /**
     * Метод формирует ответ на поисковый запрос пользователя
     *
     * @param query  поисковый запрос
     * @param site   сайт, по которому осуществляется поиск (если не задан, то поиск происходит по всем
     *               проиндексированным сайтам); задаётся в формате http://www.site.com (без слэша в конце)
     * @param offset сдвиг от 0 для постраничного вывода результата
     * @param limit  количество результатов, которое необходимо вывести
     * @return Объект {@link SearchResponse}
     */
    @Override
    public SearchResponse search(String query, String site, Integer offset, Integer limit) {
        SearchParamsValidationResult searchParamsValidationResult = validateSearchParams(query, site, offset, limit);
        if (!searchParamsValidationResult.result()) {
            log.info(searchParamsValidationResult.error());
            return new SearchResponse(false, null, null, searchParamsValidationResult.error());
        }
        Map<SiteEntity, SiteLemmasSearchResult> lemmasSearchMap = searchLemmaEntities(
                searchParamsValidationResult.siteEntity(),
                searchParamsValidationResult.lemmas().keySet()
        );
        if (lemmasSearchMap.isEmpty()) {
            return new SearchResponse(true, 0, Collections.emptyList(), null);
        }
        List<Map.Entry<PageEntity, Double>> pageRelRanks = searchPages(lemmasSearchMap);
        List<SearchItem> data = getData(pageRelRanks.stream()
                .skip(offset).limit(limit).toList(), lemmasSearchMap);
        return new SearchResponse(true, pageRelRanks.size(), data, null);
    }

    /**
     * Метод проверяет заданные пользователем поисковые параметры на корректность и применимость и разбивает
     * поисковый запрос на леммы (исключая междометия, союзы, предлоги и частицы)
     *
     * @param query  поисковый запрос
     * @param site   сайт, по которому осуществляется поиск (если не задан, то поиск происходит по всем
     *               проиндексированным сайтам); задаётся в формате http://www.site.com (без слэша в конце)
     * @param offset сдвиг от 0 для постраничного вывода результата
     * @param limit  количество результатов, которое необходимо вывести
     * @return Запись {@link SearchParamsValidationResult}
     */
    private SearchParamsValidationResult validateSearchParams(String query, String site, Integer offset, Integer limit) {
        if (query.isEmpty()) {
            return new SearchParamsValidationResult(false, null, null, EMPTY_QUERY);
        }

        if (limit < 1) {
            return new SearchParamsValidationResult(false, null, null, INVALID_LIMIT);
        }

        if (offset < 0) {
            return new SearchParamsValidationResult(false, null, null, INVALID_OFFSET);
        }

        Map<String, Integer> lemmas = new LemmaAnalyzerService().getLemmas(query, false);
        if (lemmas.isEmpty()) {
            return new SearchParamsValidationResult(false, null, null, NO_LEMMAS_IN_QUERY);
        }

        SiteEntity siteEntity = null;
        if (!site.isEmpty()) {
            Optional<SiteEntity> optionalSiteEntity = siteRepository.findByUrl(site);
            if (optionalSiteEntity.isEmpty()) {
                return new SearchParamsValidationResult(false, null, null, SITE_NOT_FOUND);
            }
            siteEntity = optionalSiteEntity.get();
        }

        if (indexingService.isAnySiteAlreadyIndexing()) {
            if (site.isEmpty()) {
                return new SearchParamsValidationResult(false, null, null, INDEXING_IN_PROCESS);
            }
            if (siteEntity.getStatus() == IndexStatusType.INDEXING) {
                return new SearchParamsValidationResult(false, null, null, SITE_INDEXING_IN_PROCESS);
            }
        }

        if (site.isEmpty()) {
            if (lemmaRepository.count() == 0) {
                return new SearchParamsValidationResult(false, null, null, NOT_INDEXED);
            }
        } else {
            if (lemmaRepository.countBySite(siteEntity) == 0) {
                return new SearchParamsValidationResult(false, null, null, SITE_NOT_INDEXED);
            }
        }

        return new SearchParamsValidationResult(true, siteEntity, lemmas, "");
    }

    /**
     * Метод ищет леммы на заданном пользователем сайте (либо на каждом проиндексированном сайте, если сайт не указан).
     * Леммы, встречающиеся чаще заданного в константе MAX_FREQUENCY_PERCENT процента от общего количества страниц на
     * сайте, исключаются из критерия поиска (исключение составляет случай, когда эта лемма единственная)
     *
     * @param siteEntity объект {@link SiteEntity} с заданным пользователем сайтом для поиска,
     *                   либо null, если поиск по всем сайтам
     * @param lemmas     список искомых лемм
     * @return Словарь Сайт {@link SiteEntity} - Результат поиска лемм {@link SiteLemmasSearchResult}
     */
    private Map<SiteEntity, SiteLemmasSearchResult> searchLemmaEntities(SiteEntity siteEntity, Set<String> lemmas) {
        Map<SiteEntity, SiteLemmasSearchResult> lemmasSearchMap = new HashMap<>();

        List<LemmaEntity> foundLemmas = siteEntity == null ?
                lemmaRepository.findAllByLemmaInAndFrequencyGreaterThan(lemmas, 0) :
                lemmaRepository.findAllBySiteAndLemmaInAndFrequencyGreaterThan(siteEntity, lemmas, 0);

        foundLemmas.sort(Comparator.comparingInt(LemmaEntity::getFrequency));

        for (LemmaEntity lemma : foundLemmas) {
            if (!lemmasSearchMap.containsKey(lemma.getSite())) {
                lemmasSearchMap.put(lemma.getSite(),
                        new SiteLemmasSearchResult(pageRepository.countBySite(lemma.getSite())));
            }
            SiteLemmasSearchResult siteLemmasSearchResult = lemmasSearchMap.get(lemma.getSite());
            siteLemmasSearchResult.setFoundLemmaCount(siteLemmasSearchResult.getFoundLemmaCount() + 1);
            if (siteLemmasSearchResult.getFoundLemmaCount() > 1 && lemma.getFrequency() * 100 /
                    siteLemmasSearchResult.getPageCount() > MAX_FREQUENCY_PERCENT) {
                log.debug("Слишком часто встречаемое на сайте " + lemma.getSite().getName() +
                        " слово: " + lemma.getLemma());
                continue;
            }
            siteLemmasSearchResult.getLemmas().add(lemma.getLemma());
            siteLemmasSearchResult.getLemmaEntities().add(lemma);
        }
        // Если на сайте не найдены все заданные леммы, то поиск по нему прекращаем
        lemmasSearchMap.entrySet().removeIf(entry -> entry.getValue().getFoundLemmaCount() < lemmas.size());
        return lemmasSearchMap;
    }

    /**
     * Метод ищет страницы, соответствующие комбинации ранее найденных лемм, и сортирует их по убыванию релевантности
     *
     * @param lemmasSearchMap словарь Сайт {@link SiteEntity} - Результат поиска лемм {@link SiteLemmasSearchResult}.
     *                        Леммы уже отсортированы в порядке возрастания частоты встречаемости
     * @return Список пар ключ-значение Страница {@link PageEntity} - Относительная релевантность
     */
    private List<Map.Entry<PageEntity, Double>> searchPages(Map<SiteEntity, SiteLemmasSearchResult> lemmasSearchMap) {
        Map<PageEntity, Double> pageRanks = new HashMap<>();
        for (Map.Entry<SiteEntity, SiteLemmasSearchResult> lemmasSearchResultEntry : lemmasSearchMap.entrySet()) {
            List<IndexEntity> indexEntities = searchSiteIndexes(lemmasSearchResultEntry.getValue().getLemmaEntities());
            for (IndexEntity indexEntity : indexEntities) {
                pageRanks.put(indexEntity.getPage(),
                        pageRanks.getOrDefault(indexEntity.getPage(), 0d) + indexEntity.getRank());
            }
        }
        Double maxRank = pageRanks.isEmpty() ? 1d : pageRanks.values().stream().max(Double::compareTo).get();
        for (Map.Entry<PageEntity, Double> pageEntityDoubleEntry : pageRanks.entrySet()) {
            pageEntityDoubleEntry.setValue(pageEntityDoubleEntry.getValue() / maxRank);
        }
        return pageRanks.entrySet().stream()
                .sorted((o1, o2) -> {
                    int cmp = Double.compare(o2.getValue(), o1.getValue());
                    if (cmp == 0) {
                        return Double.compare(o1.getKey().getId(), o2.getKey().getId());
                    }
                    return cmp;
                }).toList();
    }

    /**
     * Метод ищет индексы, соответствующие страницам заданного сайта, на которых имеется вся комбинация искомых лемм.
     * Леммы уже отсортированы по возрастанию частоты встречаемости для минимизации просматриваемых в БД страниц,
     * на каждой итерации число просматриваемых в БД страниц уменьшается
     *
     * @param lemmaEntities список лемм {@link LemmaEntity}
     * @return Список индексов {@link IndexEntity}
     */
    private List<IndexEntity> searchSiteIndexes(List<LemmaEntity> lemmaEntities) {
        List<IndexEntity> allIndexEntities = new ArrayList<>();
        List<IndexEntity> indexEntities;
        Set<PageEntity> pageEntities = new HashSet<>();
        boolean firstLemma = true;
        for (LemmaEntity lemmaEntity : lemmaEntities) {
            if (firstLemma) {
                indexEntities = indexRepository.findAllByLemma(lemmaEntity);
                firstLemma = false;
            } else {
                indexEntities = indexRepository.findAllByPageInAndLemma(pageEntities, lemmaEntity);
            }
            if (indexEntities.isEmpty()) {
                return indexEntities;
            }
            allIndexEntities.addAll(indexEntities);
            pageEntities = indexEntities.stream().map(IndexEntity::getPage).collect(Collectors.toSet());
        }
        Set<PageEntity> finalPageEntities = pageEntities;
        return allIndexEntities.stream()
                .filter(indexEntity -> finalPageEntities.contains(indexEntity.getPage())).toList();
    }

    /**
     * Метод формирует список информации о найденных страницах, требуемой для ответа на запрос пользователя
     *
     * @param pageRelRanks    список пар ключ-значение Страница {@link PageEntity} - Относительная релевантность
     *                        (см. результат {@link #searchPages(Map)})
     * @param lemmasSearchMap словарь Сайт {@link SiteEntity} - Результат поиска лемм {@link SiteLemmasSearchResult}
     *                        (см. результат {@link #searchLemmaEntities(SiteEntity, Set)})
     * @return Список объектов {@link SearchItem}
     */
    private List<SearchItem> getData(List<Map.Entry<PageEntity, Double>> pageRelRanks,
                                     Map<SiteEntity, SiteLemmasSearchResult> lemmasSearchMap) {
        @SuppressWarnings("unused") List<PageEntity> preloadAllPages = pageRepository.findAllByIdIn(pageRelRanks.stream()
                .map(value -> value.getKey().getId()).toList());
        List<SearchItem> data = new ArrayList<>();
        for (Map.Entry<PageEntity, Double> pageRank : pageRelRanks) {
            SiteEntity siteEntity = pageRank.getKey().getSite();
            Document jsoupDocument = Jsoup.parse(pageRank.getKey().getContent());

            SearchItem searchItem = new SearchItem();
            searchItem.setSite(siteEntity.getUrl());
            searchItem.setSiteName(siteEntity.getName());
            searchItem.setUri(pageRank.getKey().getPath());
            searchItem.setTitle(jsoupDocument.title());
            searchItem.setSnippet(getSnippet(jsoupDocument.text(), lemmasSearchMap.get(siteEntity).getLemmas()));
            searchItem.setRelevance(pageRank.getValue());

            data.add(searchItem);
        }
        return data;
    }

    /**
     * Метод возвращает часть исходного текста с первым найденным словом, соответствующим заданному списку лемм.
     * Данное слово выделяется тегом <b><b/>
     *
     * @param text   исходный текст
     * @param lemmas список лемм
     * @return часть текста с выделенным словом
     */
    private String getSnippet(String text, List<String> lemmas) {
        List<LemmaSearchResult> lemmaSearchResults = lemmaAnalyzer.findFirstLemmas(text, lemmas,
                MAX_SNIPPET_LENGTH, false);
        if (lemmaSearchResults.isEmpty()) {
            return text.substring(0, MAX_SNIPPET_LENGTH) + "...";
        }

        StringBuilder stringBuilder = new StringBuilder();
        int startSnippetIndex = lemmaSearchResults.get(0).startIndex();
        if (startSnippetIndex > 0) {
            stringBuilder.append("...");
            startSnippetIndex = Math.max(Math.max(text.substring(0, startSnippetIndex).lastIndexOf("."), 0),
                    startSnippetIndex - MAX_SNIPPET_LENGTH / 4);
        }
        int endSnippetIndex = Math.min(startSnippetIndex + MAX_SNIPPET_LENGTH, text.length());
        for (LemmaSearchResult lemmaSearchResult : lemmaSearchResults) {
            if (lemmaSearchResult.startIndex() > endSnippetIndex) {
                break;
            }
            stringBuilder.append(text, startSnippetIndex, lemmaSearchResult.startIndex());
            stringBuilder.append("<b>");
            stringBuilder.append(text, lemmaSearchResult.startIndex(), lemmaSearchResult.endIndex());
            stringBuilder.append("</b>");
            startSnippetIndex = lemmaSearchResult.endIndex();
        }
        if (startSnippetIndex < endSnippetIndex) {
            stringBuilder.append(text, startSnippetIndex, endSnippetIndex);
        }
        if (endSnippetIndex < text.length()) {
            stringBuilder.append("...");
        }

        return stringBuilder.toString();
    }
}
