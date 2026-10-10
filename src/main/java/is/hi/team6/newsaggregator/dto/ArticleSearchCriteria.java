package is.hi.team6.newsaggregator.dto;

import java.time.Instant;

public record ArticleSearchCriteria(
        String keyword,
        Long sourceId,
        String category,
        Instant from,
        Instant to
) {}
