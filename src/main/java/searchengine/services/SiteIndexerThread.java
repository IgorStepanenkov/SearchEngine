package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j2;
import searchengine.Application;
import searchengine.config.BotSettings;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Log4j2
@RequiredArgsConstructor
@Getter
public class SiteIndexerThread extends Thread {
    private static final String INDEXING_INTERRUPTED_BY_USER = "Индексация остановлена пользователем";
    private static final long SITE_UPDATE_MIN_PERIOD_MS = 2000L;

    private final SiteEntity siteEntity;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final BotSettings botSettings;
    private final LemmaAnalyzerService lemmaAnalyzer;
    private final String singlePagePath;
    private final Set<String> uniquePaths = new HashSet<>();
    private final WebPageLoader webPageLoader = new WebPageLoader();
    @Setter
    private volatile long lastRequestTimeMillis = 0L;
    private volatile long lastSiteUpdateTimeMillis = 0L;
    private volatile boolean isCancelled = false;
    private volatile String lastError = null;
    private String siteLink;

    /**
     * Основной метод потока {@link SiteIndexerThread} индексации сайта создает пул ForkJoinPool и запускает
     * первую дочернюю ForkJoin-задачу {@link PageIndexerTask} для обработки первой страницы сайта (в режиме
     * индексации всех сайтов), либо одной страницы (в режиме индексации конкретной страницы). Ожидает завершения
     * и по итогам обновляет в БД статус сайта.
     */
    @SuppressWarnings("resource")
    @Override
    public void run() {
        siteLink = siteEntity.getUrl().toLowerCase();
        String firstPath = singlePagePath.isEmpty() ? "/" : singlePagePath;
        uniquePaths.add(firstPath);
        lastSiteUpdateTimeMillis = System.currentTimeMillis();
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        forkJoinPool.invoke(new PageIndexerTask(firstPath, this));
        siteRepository.updateSiteStatusBySiteId(siteEntity.getId(),
                isCancelled ? IndexStatusType.FAILED : IndexStatusType.INDEXED, Instant.now(), lastError);
    }

    /**
     * Метод добавляет ссылку newLink в список уже обработанных страниц. Если количество обработанных страниц
     * достигло установленного в конфигурационном файле лимита, то ссылка не добавляется.
     *
     * @param newLink относительная ссылка на страницу сайта
     * @return True, если ссылка newLink была добавлена в список. False, если ссылка не была добавлена и ее
     * в дальнейшем обрабатывать не надо.
     */
    @Synchronized
    public boolean addNewUniqueLink(String newLink) {
        if (uniquePaths.size() < botSettings.getMaxPageCount()) {
            return uniquePaths.add(newLink.toLowerCase());
        }
        return false;
    }

    /**
     * Метод проверяет флаг необходимости прекращения индексации по требованию пользователя.
     * В переменной lastError фиксируется соответствующая информация
     *
     * @return True, если пользователь инициировал прекращение индексации
     */
    public boolean checkIfIndexingCancelled() {
        if (isCancelled) {
            return true;
        }
        if (Application.cancelIndexingProcess) {
            isCancelled = true;
            lastError = INDEXING_INTERRUPTED_BY_USER;
            log.debug(INDEXING_INTERRUPTED_BY_USER + ": " + siteLink);
            return true;
        }
        return false;
    }

    /**
     * Метод инициирует прекращение индексации в связи с ошибкой
     * В переменной lastError фиксируется соответствующая информация
     */
    public void cancelIndexing(String error) {
        isCancelled = true;
        lastError = error;
        log.debug("Остановлена индексация " + siteLink + " из-за ошибки: " + error);
    }

    /**
     * Метод обновляет время текущего статуса сайта в БД (не чаще периода SITE_UPDATE_MIN_PERIOD_MS)
     */
    public void updateSiteStatusTime() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastSiteUpdateTimeMillis > SITE_UPDATE_MIN_PERIOD_MS) {
            log.debug("Обновление времени статуса сайта: " + siteLink);
            lastSiteUpdateTimeMillis = currentTimeMillis;
            siteRepository.updateSiteStatusTimeBySiteId(siteEntity.getId(), Instant.now());
        }
    }
}
