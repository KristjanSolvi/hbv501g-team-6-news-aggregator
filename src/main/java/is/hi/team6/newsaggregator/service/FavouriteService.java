package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;

import is.hi.team6.newsaggregator.dto.ArticleSummary;

public interface FavouriteService {

    ArticleSummary saveFavourite(
            String email,
            Long articleId
    );

    Page<ArticleSummary> getFavourites(
            String email,
            int page,
            int size
    );
}