package is.hi.team6.newsaggregator.service;

import java.util.UUID;

import org.springframework.data.domain.Page;

import is.hi.team6.newsaggregator.dto.ArticleSummary;

public interface FavouriteService {

    ArticleSummary saveFavourite(
            UUID accountId,
            UUID articleId
    );

    Page<ArticleSummary> getFavourites(
            UUID accountId,
            int page,
            int size
    );
}
