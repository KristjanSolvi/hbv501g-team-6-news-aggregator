package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.CreateArticleRequest;
import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@Service
public class ArticleService {

    private final ArticleRepository articles;
    private final UserAccountRepository accounts;

    public ArticleService(ArticleRepository articles, UserAccountRepository accounts) {
        this.articles = articles;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Page<ArticleSummary> getFeed(int page, int size) {
        var order = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
        return articles.findAll(PageRequest.of(page, size, order)).map(ArticleSummary::from);
    }

    @Transactional
    public ArticleSummary createArticle(CreateArticleRequest request, String email) {
        var owner = accounts.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required"));

        var article = new Article(
                request.title(),
                request.content(),
                request.url(),
                request.category(),
                request.publishedAt(),
                null,
                owner
        );

        return ArticleSummary.from(articles.save(article));
    }
}
