package is.hi.team6.newsaggregator.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "articles", indexes = @Index(name = "article_publication_idx", columnList = "published_at,id"))
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 2048)
    private String url;

    @Column(nullable = false)
    private String category;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private NewsSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserAccount owner;

    protected Article() {}

    public Article(String title, String content, String url, String category,
                   Instant publishedAt, NewsSource source, UserAccount owner) {
        this.title = title;
        this.content = content;
        this.url = url;
        this.category = category;
        this.publishedAt = publishedAt;
        this.source = source;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getUrl() {
        return url;
    }

    public NewsSource getSource() {
        return source;
    }
}
