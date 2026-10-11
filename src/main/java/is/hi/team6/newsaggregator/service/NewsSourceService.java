package is.hi.team6.newsaggregator.service;

import is.hi.team6.newsaggregator.dto.NewsSourceResponse;
import is.hi.team6.newsaggregator.dto.NewsSourceRequest;
import is.hi.team6.newsaggregator.model.NewsSource;
import java.util.List;

public interface NewsSourceService {
    List<NewsSourceResponse> getSources();
    NewsSourceResponse createSource(NewsSourceRequest request);
    NewsSourceResponse updateSource(Long id, NewsSourceRequest request);
    void deleteSource(Long id);
    NewsSource getSource(Long id);
}
