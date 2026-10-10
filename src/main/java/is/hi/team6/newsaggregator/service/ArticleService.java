package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;

import is.hi.team6.newsaggregator.dto.ArticleCreateRequest;
import is.hi.team6.newsaggregator.dto.ArticleResponse;
import is.hi.team6.newsaggregator.dto.ArticleSummary;

public interface ArticleService {

    Page<ArticleSummary> getFeed(int page, int size);

    ArticleResponse createArticle(String email, ArticleCreateRequest request);
}
