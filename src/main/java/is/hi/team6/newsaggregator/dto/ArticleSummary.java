package is.hi.team6.newsaggregator.dto;

import java.time.Instant;
import java.util.UUID;

import is.hi.team6.newsaggregator.model.Article;

public record ArticleSummary(UUID id, String title, String category, Instant publishedAt,
                             String url, SourceSummary source) {

    public record SourceSummary(UUID id, String name, String url) {}

    public static ArticleSummary from(Article article) {
        var source = article.getSource();
        return new ArticleSummary(article.getId(), article.getTitle(), article.getCategory(),
                article.getPublishedAt(), article.getUrl(), source == null ? null :
                new SourceSummary(source.getId(), source.getName(), source.getUrl()));
    }
}
