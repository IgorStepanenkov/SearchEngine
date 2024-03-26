package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.ResultResponse;

public interface IndexingService {
    ResultResponse startIndexing();

    void startIndexingAsync();

    ResultResponse stopIndexing();

    ResultResponse startUrlIndexing(String url);

    void startUrlIndexingAsync(Site site, String url);

    boolean isAnySiteAlreadyIndexing();
}
