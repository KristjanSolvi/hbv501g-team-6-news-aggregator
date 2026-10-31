package is.hi.team6.newsaggregator.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import is.hi.team6.newsaggregator.dto.ArticleCreateRequest;
import is.hi.team6.newsaggregator.dto.ArticleResponse;
import is.hi.team6.newsaggregator.dto.ArticleSearchCriteria;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.ArticleUpdateRequest;

public interface ArticleService {

    Page<ArticleSummary> getFeed(int page, int size);
    Page<ArticleSummary> search(ArticleSearchCriteria criteria, int page, int size);

    ArticleResponse getArticle(UUID id);

    ArticleResponse createArticle(UUID accountId, ArticleCreateRequest request);

    ArticleResponse updateOwnArticle(
        UUID accountId,
        UUID id,
        ArticleUpdateRequest request
    );

    void deleteOwnArticle(UUID accountId, UUID id);

    ArticleResponse updateAnyArticle(UUID id, ArticleUpdateRequest request);

    void deleteAnyArticle(UUID id);
}
