package is.hi.team6.newsaggregator.observer;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import is.hi.team6.newsaggregator.event.ArticleAddedEvent;
import is.hi.team6.newsaggregator.service.FavouriteService;

@Component
public class FavouriteObserver {

    private final FavouriteService favourites;

    public FavouriteObserver(FavouriteService favourites) {
        this.favourites = favourites;
    }

    @EventListener
    public void onArticleAdded(ArticleAddedEvent event) {

        System.out.println(
            "Observer: New article added: "
            + event.article().getTitle()
        );

        favourites.saveFavourite(
            event.user().getId(),
            event.article().getId()
        );

        System.out.println(
            "Observer: Article automatically added to favourites."
        );
    }
};