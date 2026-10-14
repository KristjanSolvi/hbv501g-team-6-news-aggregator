
package is.hi.team6.newsaggregator.dto;

import java.time.Instant;

import is.hi.team6.newsaggregator.model.Article;

public record ArticleResponse(
        Long id,
        String title,
        String content,
        String category,
        Instant publishedAt,
        String url,
        ArticleSummary.SourceSummary source,
        Long ownerId
) {

    public static ArticleResponse from(Article article) {
        var source = article.getSource();
        var owner = article.getOwner();

        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getCategory(),
                article.getPublishedAt(),
                article.getUrl(),
                source == null ? null :
                        new ArticleSummary.SourceSummary(
                                source.getId(),
                                source.getName(),
                                source.getUrl()
                        ),
                owner == null ? null : owner.getId()
        );
    }
}