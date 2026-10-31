package is.hi.team6.newsaggregator.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.model.Favourite;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.FavouriteRepository;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@Service
public class FavouriteServiceImpl implements FavouriteService {

    private final FavouriteRepository favourites;
    private final UserAccountRepository accounts;
    private final ArticleRepository articles;

    public FavouriteServiceImpl(
            FavouriteRepository favourites,
            UserAccountRepository accounts,
            ArticleRepository articles) {
        this.favourites = favourites;
        this.accounts = accounts;
        this.articles = articles;
    }

    @Override
    @Transactional
    public ArticleSummary saveFavourite(
            String email,
            Long articleId) {

        var user = accounts.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Account not found"
                        ));

        var article = articles.findById(articleId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Article not found"
                        ));

        if (favourites.existsByUserAndArticle(user, article)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Article is already saved as a favourite"
            );
        }

        try {
            favourites.saveAndFlush(
                    new Favourite(user, article)
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Article is already saved as a favourite"
            );
        }

        return ArticleSummary.from(article);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ArticleSummary> getFavourites(
            String email,
            int page,
            int size) {

        var user = accounts.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Account not found"
                        ));

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "id"
                )
        );

        return favourites.findByUser(user, pageable)
                .map(favourite ->
                        ArticleSummary.from(
                                favourite.getArticle()
                        ));
    }
}