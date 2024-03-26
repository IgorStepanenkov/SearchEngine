package searchengine.services.helpers;

import lombok.Data;
import org.jsoup.nodes.Document;

@Data
public class WebPageLoaderResponse {
    private final int statusCode;
    private final boolean isLoaded;
    private final Document jsoupDocument;
}
