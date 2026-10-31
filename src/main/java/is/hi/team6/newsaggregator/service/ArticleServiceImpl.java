package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.domain.Specification;

import is.hi.team6.newsaggregator.dto.ArticleCreateRequest;
import is.hi.team6.newsaggregator.dto.ArticleResponse;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.ArticleSearchCriteria;
import is.hi.team6.newsaggregator.dto.ArticleUpdateRequest;
import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.NewsSourceRepository;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;
import is.hi.team6.newsaggregator.repository.FavouriteRepository;

@Service
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articles;
    private final UserAccountRepository accounts;
    private final NewsSourceRepository sources;
    private final FavouriteRepository favourites;

    public ArticleServiceImpl( ArticleRepository articles, UserAccountRepository accounts, NewsSourceRepository sources, FavouriteRepository favourites) {
        this.articles = articles;
        this.accounts = accounts;
        this.sources = sources;
        this.favourites = favourites;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ArticleSummary> getFeed(int page, int size) {
        var order = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
        return articles.findAll(PageRequest.of(page, size, order)).map(ArticleSummary::from);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ArticleSummary> search(
            ArticleSearchCriteria criteria, int page, int size) {

        var order = Sort.by(Sort.Direction.DESC, "publishedAt", "id");

        Specification<Article> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String keyword = "%" + criteria.keyword().trim().toLowerCase() + "%";

                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), keyword),
                        cb.like(cb.lower(root.get("content")), keyword)
                ));
            }

            if (criteria.sourceId() != null) {
                predicates.add(cb.equal(root.get("source").get("id"), criteria.sourceId()));
            }

            if (criteria.category() != null && !criteria.category().isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("category")),
                        criteria.category().trim().toLowerCase()
                ));
            }

            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("publishedAt"), criteria.from()
                ));
            }

            if (criteria.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("publishedAt"), criteria.to()
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return articles.findAll(spec, PageRequest.of(page, size, order))
                .map(ArticleSummary::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse getArticle(Long id) {
        var article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));
        return ArticleResponse.from(article);
    }

    @Override
    @Transactional
    public ArticleResponse createArticle(String email, ArticleCreateRequest request) {
        var owner = accounts.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Login required"));

        var source = request.sourceId() == null
                ? null
                : sources.findById(request.sourceId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "News source not found"));

        var article = new Article(
                request.title(),
                request.content(),
                request.url(),
                request.category(),
                request.publishedAt(),
                source,
                owner
        );

        return ArticleResponse.from(articles.save(article));
    }

    @Override
    @Transactional
    public ArticleResponse updateOwnArticle(
            String email,
            Long id,
            ArticleUpdateRequest request) {

        var article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Article not found"));

        var owner = article.getOwner();

        if (owner == null || !owner.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own articles");
        }

        article.update(request);

        return ArticleResponse.from(articles.save(article));
    }

    @Override
    @Transactional
    public void deleteOwnArticle(String email, Long id) {

        var article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Article not found"));

        var owner = article.getOwner();

        if (owner == null || !owner.getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own articles");
        }

        favourites.deleteByArticle(article);
        articles.delete(article);
    }


    @Override
    @Transactional
    public ArticleResponse updateAnyArticle(
            Long id,
            ArticleUpdateRequest request) {

        var article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Article not found"));

        article.update(request);

        return ArticleResponse.from(articles.save(article));
    }

    @Override
    @Transactional
    public void deleteAnyArticle(Long id) {

        var article = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Article not found"));

        favourites.deleteByArticle(article);
        articles.delete(article);
    }
}
