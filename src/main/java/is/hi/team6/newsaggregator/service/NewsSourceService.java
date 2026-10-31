package is.hi.team6.newsaggregator.service;

import java.util.UUID;

import is.hi.team6.newsaggregator.dto.NewsSourceResponse;
import is.hi.team6.newsaggregator.dto.NewsSourceRequest;
import is.hi.team6.newsaggregator.model.NewsSource;
import java.util.List;

public interface NewsSourceService {
    List<NewsSourceResponse> getSources();
    NewsSourceResponse createSource(NewsSourceRequest request);
    NewsSourceResponse updateSource(UUID id, NewsSourceRequest request);
    void deleteSource(UUID id);
    NewsSource getSource(UUID id);
}
