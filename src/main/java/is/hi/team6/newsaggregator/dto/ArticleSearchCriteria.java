package is.hi.team6.newsaggregator.dto;

import java.time.Instant;
import java.util.UUID;

public record ArticleSearchCriteria(
        String keyword,
        UUID sourceId,
        String category,
        Instant from,
        Instant to
) {}
