package is.hi.team6.newsaggregator.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.ArticleImportRequest;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.service.ArticleImportService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final ArticleImportService imports;

    public AdminArticleController(ArticleImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ArticleSummary> importArticles(
            @Valid @RequestBody ArticleImportRequest request) {
        return imports.importArticles(request);
    }
}