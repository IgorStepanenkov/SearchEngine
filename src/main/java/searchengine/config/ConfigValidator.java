package searchengine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import static searchengine.services.UrlTools.isSiteUrlOnly;

@Log4j2
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ConfigValidator implements CommandLineRunner {
    private static final String ERROR_NO_URLS = "В конфигурационном файле не задан список сайтов";
    private static final String ERROR_URL_NOT_VALID = "В конфигурационном файле указан некорректный адрес сайта %s: " +
            " %s (корректный пример: http://www.somesite.ru или https://www.somesecsite.ru)";

    private final SitesList sites;

    @Override
    public void run(String... args) throws Exception {
        log.info("Приложение запущено");
        if (sites.getSites().isEmpty()) {
            log.error(ERROR_NO_URLS);
            System.exit(1);
            throw new Exception(ERROR_NO_URLS);
        }
        for (Site site : sites.getSites()) {
            if (!isSiteUrlOnly(site.getUrl())) {
                log.error(String.format(ERROR_URL_NOT_VALID, site.getName(), site.getUrl()));
                System.exit(1);
            }
        }
    }
}
