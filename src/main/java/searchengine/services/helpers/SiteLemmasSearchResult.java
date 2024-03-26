package searchengine.services.helpers;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.LemmaEntity;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class SiteLemmasSearchResult {
    private long pageCount;
    private int foundLemmaCount;
    private final List<String> lemmas;
    private final List<LemmaEntity> lemmaEntities;

    public SiteLemmasSearchResult(long pageCount) {
        this.pageCount = pageCount;
        foundLemmaCount = 0;
        lemmas = new ArrayList<>();
        lemmaEntities = new ArrayList<>();
    }
}
