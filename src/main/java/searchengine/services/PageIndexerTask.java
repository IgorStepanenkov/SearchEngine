package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import searchengine.services.helpers.WebPageLoaderResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@Log4j2
@RequiredArgsConstructor
public class PageIndexerTask extends RecursiveAction {
    private final String path;
    private final SiteIndexerThread parentSiteThread;

    /**
     * Основной метод ForkJoin-задачи {@link PageIndexerTask} загружает очередную страницу, индексирует ее,
     * находит на ней дочерние ссылки, запускает новые ForkJoin-задачи {@link PageIndexerTask} и ожидает их исполнения
     */
    @Override
    protected void compute() {
        if (parentSiteThread.checkIfIndexingCancelled()) {
            return;
        }
        log.debug("Запущен поток загрузки и обработки страницы: " + parentSiteThread.getSiteLink() + path);
        WebPageLoaderResponse webPageLoaderResponse = parentSiteThread.getWebPageLoader()
                .loadWebPage(parentSiteThread.getSiteLink() + path, parentSiteThread);
        if (parentSiteThread.checkIfIndexingCancelled()) {
            return;
        }
        WebPageIndexer webPageIndexer = new WebPageIndexer(path, webPageLoaderResponse, parentSiteThread);
        webPageIndexer.indexWebPage();
        Set<String> links = webPageIndexer.parseWebPage();
        if (!parentSiteThread.getSinglePagePath().isEmpty()) {
            return;
        }
        if (links.isEmpty()) {
            return;
        }
        if (parentSiteThread.checkIfIndexingCancelled()) {
            return;
        }
        startChildPagesTasksAndWait(links);
    }

    /**
     * Метод запускает дочерние ForkJoin-задачи {@link PageIndexerTask} по обработке страниц из списка links и ожидает
     * их завершения
     *
     * @param links список ссылок для обработки
     */
    private void startChildPagesTasksAndWait(Set<String> links) {
        List<PageIndexerTask> taskList = new ArrayList<>();
        for (String newLink : links) {
            if (!parentSiteThread.addNewUniqueLink(newLink)) {
                continue;
            }
            PageIndexerTask task = new PageIndexerTask(newLink, parentSiteThread);
            task.fork();
            taskList.add(task);
        }
        for (PageIndexerTask task : taskList) {
            task.join();
        }
    }
}
