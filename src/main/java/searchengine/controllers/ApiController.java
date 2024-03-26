package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ResultResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@SuppressWarnings("JavadocLinkAsPlainText")
@Log4j2
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    /**
     * Метод формирует ответ на запрос статистики
     *
     * @return Объект {@link ResponseEntity<ResultResponse>}
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    /**
     * Метод формирует ответ на запрос старта индексации всех сайтов
     *
     * @return Объект {@link ResponseEntity<ResultResponse>}
     */
    @GetMapping("/startIndexing")
    public ResponseEntity<ResultResponse> startIndexing() {
        log.info("Обработка запроса startIndexing");
        ResultResponse resultResponse = indexingService.startIndexing();
        return ResponseEntity.status(resultResponse.isResult() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(resultResponse);
    }

    /**
     * Метод формирует ответ на запрос остановки индексации
     *
     * @return Объект {@link ResponseEntity<ResultResponse>}
     */
    @GetMapping("/stopIndexing")
    public ResponseEntity<ResultResponse> stopIndexing() {
        log.info("Обработка запроса stopIndexing");
        ResultResponse resultResponse = indexingService.stopIndexing();
        return ResponseEntity.status(resultResponse.isResult() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(resultResponse);
    }

    /**
     * Метод формирует ответ на запрос старта индексации заданной страницы
     *
     * @param url адрес страницы для индексации
     * @return Объект {@link ResponseEntity<ResultResponse>}
     */
    @PostMapping("/indexPage")
    public ResponseEntity<ResultResponse> indexPage(@RequestParam(name = "url", defaultValue = "") String url) {
        log.info("Обработка запроса indexPage");
        ResultResponse resultResponse = indexingService.startUrlIndexing(url);
        return ResponseEntity.status(resultResponse.isResult() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(resultResponse);
    }

    /**
     * Метод формирует ответ на поисковый запрос пользователя
     *
     * @param query  поисковый запрос
     * @param site   сайт, по которому осуществляется поиск (если не задан, то поиск происходит по всем
     *               проиндексированным сайтам); задаётся в формате http://www.site.com (без слэша в конце)
     * @param offset сдвиг от 0 для постраничного вывода результата
     * @param limit  количество результатов, которое необходимо вывести
     * @return Объект {@link ResponseEntity<SearchResponse>}
     */
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query", defaultValue = "") String query,
                                                 @RequestParam(name = "site", defaultValue = "") String site,
                                                 @RequestParam(name = "offset", defaultValue = "0") Integer offset,
                                                 @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        log.info("Обработка запроса search");
        SearchResponse searchResponse = searchService.search(query, site, offset, limit);
        return ResponseEntity.status(searchResponse.isResult() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(searchResponse);
    }

}
