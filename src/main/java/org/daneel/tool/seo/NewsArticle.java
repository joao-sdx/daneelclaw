package org.daneel.tool.seo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewsArticle(
    @JsonProperty("result_id") String resultId,
    String title,
    String url,
    String domain,
    String published) {}
