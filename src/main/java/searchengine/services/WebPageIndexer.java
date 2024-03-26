package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jsoup.nodes.Element;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.helpers.WebPageLoaderResponse;

import java.util.*;
import java.util.stream.Collectors;

import static searchengine.services.UrlTools.getSiteLink;
import static searchengine.services.UrlTools.isNonHtmlExtension;

@SuppressWarnings("CallToPrintStackTrace")
@Log4j2
@RequiredArgsConstructor
public class WebPageIndexer {
    private final String path;
    private final WebPageLoaderResponse webPageLoaderResponse;
    private final SiteIndexerThread parentSiteThread;

    /**
     * Метод находит в ранее загруженном документе Jsoup все теги <A></A> со ссылками на страницы этого же сайта.
     * Ссылки на метки #, а также на некоторые не HTML-файлы отбрасываются.
     *
     * @return Список найденных ссылок (относительных, т.е. без baseURI)
     */
    public Set<String> parseWebPage() {
        Set<String> result = new HashSet<>();

        if (!webPageLoaderResponse.isLoaded()) {
            return result;
        }

        String siteLink = getSiteLink(webPageLoaderResponse.getJsoupDocument().baseUri()).toLowerCase();
        for (Element anchor : webPageLoaderResponse.getJsoupDocument().select("a")) {
            String href = anchor.attr("abs:href").trim();
            if (href.contains("#") || isNonHtmlExtension(href.toLowerCase())) {
                continue;
            }
            if (href.toLowerCase().startsWith(siteLink)) {
                href = href.substring(siteLink.length());
            }
            if (!href.startsWith("/")) {
                continue;
            }
            result.add(href);
        }

        return result;
    }

    /**
     * Метод индексирует ранее загруженный документ Jsoup и сохраняет данные в БД
     */
    public void indexWebPage() {
        if (parentSiteThread.checkIfIndexingCancelled()) {
            return;
        }
        Map<String, Integer> lemmaMap = webPageLoaderResponse.isLoaded() ? parentSiteThread.getLemmaAnalyzer()
                .getLemmas(webPageLoaderResponse.getJsoupDocument().text(), false) : Collections.emptyMap();
        synchronized (WebPageIndexer.class) {
            if (parentSiteThread.checkIfIndexingCancelled()) {
                return;
            }
            parentSiteThread.updateSiteStatusTime();
            PageEntity pageEntity = insertPage();
            if (lemmaMap.isEmpty()) {
                return;
            }
            try {
                Map<String, LemmaEntity> lemmaEntityMap = insertOrIncrementLemmasFrequency(parentSiteThread.getSiteEntity(),
                        lemmaMap.keySet().stream().toList());
                insertIndexes(pageEntity, lemmaMap, lemmaEntityMap);
            } catch (Exception ex) {
                parentSiteThread.cancelIndexing(ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Метод добавляет в БД новую web-страницу
     *
     * @return Страница сайта {@link  PageEntity}
     */
    private PageEntity insertPage() {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(parentSiteThread.getSiteEntity());
        pageEntity.setPath(path);
        pageEntity.setCode(webPageLoaderResponse.getStatusCode());
        pageEntity.setContent(webPageLoaderResponse.isLoaded() ? webPageLoaderResponse.getJsoupDocument().html() : "");
        return parentSiteThread.getPageRepository().saveAndFlush(pageEntity);
    }

    /**
     * Метод добавляет новые леммы по индексируемому сайту, либо увеличивает количество существующих в базе лемм.
     *
     * @param siteEntity индексируемый сайт
     * @param lemmas     список текстовых лемм для добавления/инкрементирования
     * @return Словарь лемм из БД (текстовая лемма - лемма {@link LemmaEntity})
     */
    private Map<String, LemmaEntity> insertOrIncrementLemmasFrequency(SiteEntity siteEntity, List<String> lemmas) {
        List<LemmaEntity> lemmaEntityList = parentSiteThread.getLemmaRepository().findAllBySiteIdAndLemmaIn(
                siteEntity.getId(), lemmas
        );
        if (!lemmaEntityList.isEmpty()) {
            parentSiteThread.getLemmaRepository().incrementFrequencyAllByLemmaIdIn(lemmaEntityList.stream()
                    .map(LemmaEntity::getId).toList());
        }
        if (lemmas.size() > lemmaEntityList.size()) {
            List<String> existedLemmas = lemmaEntityList.stream().map(LemmaEntity::getLemma).toList();
            List<LemmaEntity> newLemmaEntities = new ArrayList<>();
            for (String lemma : lemmas) {
                if (existedLemmas.contains(lemma)) {
                    continue;
                }
                LemmaEntity newLemmaEntity = new LemmaEntity();
                newLemmaEntity.setSite(siteEntity);
                newLemmaEntity.setLemma(lemma);
                newLemmaEntity.setFrequency(1);
                newLemmaEntities.add(newLemmaEntity);
            }
            parentSiteThread.getLemmaRepository().saveAllAndFlush(newLemmaEntities);
            lemmaEntityList.addAll(newLemmaEntities);
        }
        return lemmaEntityList.stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, lemmaEntity -> lemmaEntity));
    }

    /**
     * Метод добавляет новые индексы по индексируемой странице.
     *
     * @param pageEntity     - индексируемая страница
     * @param lemmaMap       - словарь найденных на странице лемм (лемма - количество вхождений)
     * @param lemmaEntityMap - словарь лемм из БД (текстовая лемма - лемма {@link LemmaEntity}), соответствующий
     *                       найденным на странице леммам
     */
    private void insertIndexes(PageEntity pageEntity, Map<String, Integer> lemmaMap,
                               Map<String, LemmaEntity> lemmaEntityMap) {
        List<IndexEntity> newIndexEntities = new ArrayList<>();
        for (Map.Entry<String, Integer> lemmaEntry : lemmaMap.entrySet()) {
            IndexEntity newIndexEntity = new IndexEntity();
            newIndexEntity.setPage(pageEntity);
            newIndexEntity.setRank(lemmaEntry.getValue().floatValue());
            LemmaEntity lemmaEntity = lemmaEntityMap.getOrDefault(lemmaEntry.getKey(), null);
            if (lemmaEntity == null) {
                log.warn("Лемма '" + lemmaEntry.getKey() + "' не найдена среди существующих лемм для страницы: " +
                        path);
                continue;
            }
            newIndexEntity.setLemma(lemmaEntity);
            newIndexEntities.add(newIndexEntity);
        }
        if (!newIndexEntities.isEmpty()) {
            parentSiteThread.getIndexRepository().saveAllAndFlush(newIndexEntities);
        }
    }

}
