package is.hi.team6.newsaggregator.dto;

import is.hi.team6.newsaggregator.model.NewsSource;

public record NewsSourceResponse(
    Long id,
    String name,
    String url
) 
{
    public static NewsSourceResponse from(NewsSource source) {
        return new NewsSourceResponse(source.getId(), source.getName(), source.getUrl());
    }
}