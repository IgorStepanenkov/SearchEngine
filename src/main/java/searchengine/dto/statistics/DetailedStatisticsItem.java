package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private String status;
    private long statusTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

    private long pages;
    private long lemmas;
}
