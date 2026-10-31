package is.hi.team6.newsaggregator.controller;

import java.util.UUID;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.service.FavouriteService;
import is.hi.team6.newsaggregator.service.AccountUserDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/favourites")
public class FavouriteController {

    private final FavouriteService favourites;

    public FavouriteController(
            FavouriteService favourites) {
        this.favourites = favourites;
    }

    @PostMapping("/{articleId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleSummary saveFavourite(
            @PathVariable UUID articleId,
            @AuthenticationPrincipal AccountUserDetails principal) {

        return favourites.saveFavourite(
                principal.getAccountId(),
                articleId
        );
    }

    @GetMapping
    public PagedModel<ArticleSummary> getFavourites(
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size,

            @AuthenticationPrincipal AccountUserDetails principal) {

        return new PagedModel<>(
                favourites.getFavourites(
                        principal.getAccountId(),
                        page,
                        size
                )
        );
    }
}
