package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.services.helpers.WebPageLoaderResponse;

import java.io.IOException;

import static java.lang.Thread.sleep;

@Log4j2
public class WebPageLoader {
    /**
     * Метод загружает web-страницу с применением Jsoup. Между последовательными загрузками обеспечивается блокировка
     * с задержкой на период, указанный в конфигурации приложения
     *
     * @param webPageLink адрес загружаемой web-страницы
     * @param parentSiteThread ссылка на родительский поток, из которого был запущен обход данного сайта
     * @return Результат загрузки {@link WebPageLoaderResponse} (статус-код, признак удачной загрузки,
     * при удачной загрузке - документ Jsoup)
     */
    public WebPageLoaderResponse loadWebPage(String webPageLink, SiteIndexerThread parentSiteThread) {
        Connection.Response response;
        Document jsoupDocument;
        synchronized (this) {
            if (parentSiteThread.checkIfIndexingCancelled()) {
                return new WebPageLoaderResponse(0, false, null);
            }
            long currentTimeMillis = System.currentTimeMillis();
            long delayMillis = parentSiteThread.getBotSettings().getMinDelay() -
                    (currentTimeMillis - parentSiteThread.getLastRequestTimeMillis());
            if (delayMillis > 0) {
                try {
                    sleep(delayMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                log.debug("Задержка " + delayMillis + " мс перед загрузкой " + webPageLink);
            }
            parentSiteThread.setLastRequestTimeMillis(System.currentTimeMillis());
        }
        if (parentSiteThread.checkIfIndexingCancelled()) {
            return new WebPageLoaderResponse(0, false, null);
        }

        try {
            response = Jsoup.connect(webPageLink)
                    .userAgent(parentSiteThread.getBotSettings().getUserAgent())
                    .referrer(parentSiteThread.getBotSettings().getReferrer())
                    .execute();
        } catch (HttpStatusException e) {
            log.debug("Не удалось загрузить web-страницу " + webPageLink +
                    ": statusCode = " + e.getStatusCode());
            return new WebPageLoaderResponse(e.getStatusCode(), false, null);
        } catch (IOException e) {
            log.debug("Не удалось загрузить web-страницу " + webPageLink +
                    ": " + e.getMessage());
            return new WebPageLoaderResponse(0, false, null);
        }

        try {
            jsoupDocument = response.parse();
        } catch (IOException e) {
            log.debug("Не удалось загрузить web-страницу " + webPageLink +
                    " (" + response.statusCode() + "): " + e.getMessage());
            return new WebPageLoaderResponse(0, false, null);
        }
        return new WebPageLoaderResponse(response.statusCode(), true, jsoupDocument);
    }
}
