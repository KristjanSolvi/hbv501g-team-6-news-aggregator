package is.hi.team6.newsaggregator.event;

import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.UserAccount;

public record ArticleAddedEvent(
        UserAccount user,
        Article article
) {};
