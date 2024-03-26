package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResultResponse {
    private boolean result;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;
}
