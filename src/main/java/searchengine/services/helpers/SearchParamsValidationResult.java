package searchengine.services.helpers;

import searchengine.model.SiteEntity;

import java.util.Map;

public record SearchParamsValidationResult(boolean result, SiteEntity siteEntity, Map<String, Integer> lemmas,
                                           String error) {
}
