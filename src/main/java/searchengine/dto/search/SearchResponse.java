package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResponse {
    private boolean result;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer count;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SearchItem> data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;
}
