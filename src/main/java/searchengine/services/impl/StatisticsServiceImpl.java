package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.Application;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    /**
     * Метод формирует ответ на запрос статистики
     *
     * @return Объект {@link StatisticsResponse}
     */
    @Override
    public StatisticsResponse getStatistics() {
        List<SiteEntity> siteEntities = siteRepository.findAll();

        TotalStatistics total = new TotalStatistics();
        total.setSites(siteEntities.size());
        total.setIndexing(Application.isIndexingInProcess);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (SiteEntity siteEntity : siteEntities) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteEntity.getName());
            item.setUrl(siteEntity.getUrl());
            long pages = pageRepository.countBySite(siteEntity);
            long lemmas = lemmaRepository.countBySite(siteEntity);
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteEntity.getStatus().toString());
            item.setError(siteEntity.getLastError());
            item.setStatusTime(siteEntity.getStatusTime().toEpochMilli());
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
