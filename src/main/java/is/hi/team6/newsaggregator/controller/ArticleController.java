package is.hi.team6.newsaggregator.controller;

import java.security.Principal;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.CreateArticleRequest;
import is.hi.team6.newsaggregator.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articles;

    public ArticleController(ArticleService articles) {
        this.articles = articles;
    }

    @GetMapping
    public PagedModel<ArticleSummary> getFeed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(articles.getFeed(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleSummary createArticle(
            @Valid @RequestBody CreateArticleRequest request,
            Principal principal) {
        return articles.createArticle(request, principal.getName());
    }
}
