package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.BotSettings;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.ResultResponse;
import searchengine.model.IndexEntity;
import searchengine.model.IndexStatusType;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.LemmaAnalyzerService;
import searchengine.services.SiteIndexerThread;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static searchengine.Application.cancelIndexingProcess;
import static searchengine.Application.isIndexingInProcess;
import static searchengine.services.UrlTools.getSiteLink;
import static searchengine.services.UrlTools.isSiteUrlOnly;

@Log4j2
@Service(value = "indexingService")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class IndexingServiceImpl implements IndexingService {
    private static final String INDEXING_ALREADY_IN_PROCESS = "Индексация уже запущена";
    private static final String INDEXING_NOT_IN_PROCESS = "Индексация не запущена";
    private static final String INVALID_URL = "Данная страница находится за пределами сайтов, " +
            "указанных в конфигурационном файле";
    private static final String INDEXING_INTERRUPTED_BY_USER = "Индексация остановлена пользователем";
    private static final long INDEXING_INTERRUPT_WAIT_TIMEOUT = 200L;
    private static final int INDEXING_INTERRUPT_WAIT_TRIES = 20;

    private final ApplicationContext context;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sites;
    private final BotSettings botSettings;
    private final LemmaAnalyzerService lemmaAnalyzer;

    /**
     * Метод проверяет возможность запуска индексации всех сайтов, если запуск возможен - запускает индексацию
     * в отдельном потоке, и сразу формирует соответствующий ответ пользователю
     *
     * @return Объект {@link ResultResponse}
     */
    @Override
    public ResultResponse startIndexing() {
        if (isAnySiteAlreadyIndexing()) {
            log.info(INDEXING_ALREADY_IN_PROCESS);
            return new ResultResponse(false, INDEXING_ALREADY_IN_PROCESS);
        }
        getIndexingService().startIndexingAsync();
        return new ResultResponse(true, null);
    }

    /**
     * Асинхронный метод запускает индексацию каждого сайта в отдельном потоке {@link SiteIndexerThread} и
     * ожидает, когда работа потоков завершится
     */
    @Async()
    @Override
    public void startIndexingAsync() {
        synchronized (this) {
            if (isIndexingInProcess) {
                return;
            }
            isIndexingInProcess = true;
        }
        log.info("Основной поток индексации: запуск индексации сайтов");
        cancelIndexingProcess = false;
        List<Thread> siteIndexerThreadList = new ArrayList<>();
        for (Site site : sites.getSites()) {
            SiteEntity siteEntity = cleanUpAndPrepareSite(site);
            SiteIndexerThread siteIndexerThread = new SiteIndexerThread(
                    siteEntity, siteRepository, pageRepository, lemmaRepository,
                    indexRepository, botSettings, lemmaAnalyzer, ""
            );
            siteIndexerThreadList.add(siteIndexerThread);
        }
        siteIndexerThreadList.forEach(Thread::start);
        for (Thread thread : siteIndexerThreadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        cancelIndexingProcess = false;
        isIndexingInProcess = false;
        log.info("Основной поток индексации: индексация сайтов завершена");
    }

    /**
     * Метод проверяет возможность остановки индексации, при необходимости останавливает ее
     * и формирует соответствующий ответ
     *
     * @return Объект {@link ResultResponse}
     */
    @Override
    public ResultResponse stopIndexing() {
        if (!isAnySiteAlreadyIndexing()) {
            log.info(INDEXING_NOT_IN_PROCESS);
            return new ResultResponse(false, INDEXING_NOT_IN_PROCESS);
        }
        cancelIndexingProcess = true;
        int i = INDEXING_INTERRUPT_WAIT_TRIES;
        while (i-- > 0 && isIndexingInProcess) {
            try {
                Thread.sleep(INDEXING_INTERRUPT_WAIT_TIMEOUT);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (isIndexingInProcess || siteRepository.countAllByStatus(IndexStatusType.INDEXING) > 0) {
            log.info("Принудительно меняем статус индексируемых сайтов на INDEXING_INTERRUPTED_BY_USER");
            isIndexingInProcess = false;
            siteRepository.updateAllSitesStatus(IndexStatusType.INDEXING, IndexStatusType.FAILED,
                    Instant.now(), INDEXING_INTERRUPTED_BY_USER);
        }
        return new ResultResponse(true, null);
    }

    /**
     * Метод проверяет возможность запуска индексации заданной страницы, если запуск возможен - запускает индексацию
     * в отдельном потоке, и сразу формирует соответствующий ответ пользователю
     *
     * @param url адрес страницы для индексации
     * @return Объект {@link ResultResponse}
     */
    @Override
    public ResultResponse startUrlIndexing(String url) {
        if (isAnySiteAlreadyIndexing()) {
            log.info(INDEXING_ALREADY_IN_PROCESS);
            return new ResultResponse(false, INDEXING_ALREADY_IN_PROCESS);
        }
        url = url.trim();
        if (isSiteUrlOnly(url)) {
            url += "/";
        }
        String siteUrl = getSiteLink(url);
        Optional<Site> optSite = sites.getSites().stream()
                .filter(siteObj -> siteObj.getUrl().equals(siteUrl)).findFirst();
        if (optSite.isEmpty()) {
            log.info(INVALID_URL);
            return new ResultResponse(false, INVALID_URL);
        }
        getIndexingService().startUrlIndexingAsync(optSite.get(), url);
        return new ResultResponse(true, null);
    }

    /**
     * Асинхронный метод запускает индексацию заданной страницы
     *
     * @param site сайт {@link Site}, которому принадлежит url
     * @param url  адрес страницы для индексации
     */
    @Async
    public void startUrlIndexingAsync(Site site, String url) {
        synchronized (this) {
            if (isIndexingInProcess) {
                return;
            }
            isIndexingInProcess = true;
        }
        log.info("Поток индексации страницы: запуск индексации страницы: " + url);
        cancelIndexingProcess = false;

        String path = url.substring(site.getUrl().length());
        SiteEntity siteEntity = cleanUpPageAndPrepareSite(site, path);

        SiteIndexerThread siteIndexerThread = new SiteIndexerThread(
                siteEntity, siteRepository, pageRepository, lemmaRepository,
                indexRepository, botSettings, lemmaAnalyzer, path
        );
        siteIndexerThread.start();
        try {
            siteIndexerThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        cancelIndexingProcess = false;
        isIndexingInProcess = false;
        log.info("Поток индексации страницы: индексация страницы завершена: " + url);
    }

    /**
     * Метод проверяет, запущен ли процесс индексации по любому из сайтов
     *
     * @return True, если процесс индексации уже запущен
     */
    @Override
    public boolean isAnySiteAlreadyIndexing() {
        if (isIndexingInProcess) {
            log.debug("Проверка статуса: поток индексации активен");
            return true;
        }

        List<SiteEntity> indexingSites = siteRepository.findAllByStatus(IndexStatusType.INDEXING);
        List<String> indexingSitesUrls = indexingSites.stream().map(SiteEntity::getUrl).toList();
        for (Site site : sites.getSites()) {
            if (indexingSitesUrls.contains(site.getUrl())) {
                log.debug("Проверка статуса: в БД имеются сайты в статусе INDEXING");
                return true;
            }
        }
        log.debug("Проверка статуса: индексация не активна");
        return false;
    }

    /**
     * Метод возвращает бин indexingService (необходим для запуска асинхронных методов)
     *
     * @return Бин indexingService
     */
    private IndexingService getIndexingService() {
        return (IndexingService) context.getBean("indexingService");
    }

    /**
     * Метод очищает БД от всей информации по сайту и создает для сайта новую запись
     *
     * @param site сайт из конфигурации {@link PageEntity}
     * @return сайт из БД {@link SiteEntity}
     */
    private SiteEntity cleanUpAndPrepareSite(Site site) {
        siteRepository.findByUrl(site.getUrl()).ifPresent(siteEntity -> {
                    indexRepository.deleteAllBySiteId(siteEntity.getId());
                    lemmaRepository.deleteAllBySiteId(siteEntity.getId());
                    pageRepository.deleteAllBySiteId(siteEntity.getId());
                    siteRepository.delete(siteEntity);
                }
        );
        return insertSite(site);
    }

    /**
     * Метод очищает БД от информации об индексируемой странице (если она есть в БД). Если такого сайта еще нет в БД,
     * то он добавляется
     *
     * @param site сайт из конфигурации {@link PageEntity}
     * @param path относительный путь к индексируемой странице
     * @return сайт из БД {@link SiteEntity}
     */
    private SiteEntity cleanUpPageAndPrepareSite(Site site, String path) {
        SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl()).orElseGet(() -> insertSite(site));
        siteRepository.updateSiteStatusBySiteId(siteEntity.getId(), IndexStatusType.INDEXING,
                Instant.now(), "");
        pageRepository.findBySiteAndPath(siteEntity, path).ifPresent(this::deletePage);
        return siteEntity;
    }

    /**
     * Метод добавляет в БД новый сайт
     *
     * @param site сайт {@link Site}
     * @return Новый объект {@link SiteEntity}
     */
    private SiteEntity insertSite(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setStatus(IndexStatusType.INDEXING);
        siteEntity.setStatusTime(Instant.now());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        return siteRepository.save(siteEntity);
    }

    /**
     * Метод удаляет из БД заданную страницу и соответствующие ей индексы, а также уменьшает
     * количество соответствующих лемм
     *
     * @param pageEntity страница {@link PageEntity}
     */
    private void deletePage(PageEntity pageEntity) {
        List<IndexEntity> indexEntities = indexRepository.findAllByPage(pageEntity);
        if (!indexEntities.isEmpty()) {
            lemmaRepository.decrementFrequencyAllByLemmaIdIn(indexEntities.stream()
                    .map(indexEntity -> indexEntity.getLemma().getId())
                    .toList());
        }
        indexRepository.deleteAllByPageId(pageEntity.getId());
        pageRepository.delete(pageEntity);
    }
}
