package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;

import is.hi.team6.newsaggregator.dto.ArticleCreateRequest;
import is.hi.team6.newsaggregator.dto.ArticleResponse;
import is.hi.team6.newsaggregator.dto.ArticleSearchCriteria;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.ArticleUpdateRequest;

public interface ArticleService {

    Page<ArticleSummary> getFeed(int page, int size);
    Page<ArticleSummary> search(ArticleSearchCriteria criteria, int page, int size);

    ArticleResponse getArticle(Long id);

    ArticleResponse createArticle(String email, ArticleCreateRequest request);

    ArticleResponse updateOwnArticle(
        String email,
        Long id,
        ArticleUpdateRequest request
    );

    void deleteOwnArticle(String email, Long id);

    ArticleResponse updateAnyArticle(Long id, ArticleUpdateRequest request);

    void deleteAnyArticle(Long id);
}
