
package is.hi.team6.newsaggregator.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.ArticleImportRequest;
import is.hi.team6.newsaggregator.dto.ArticleResponse;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.dto.ArticleUpdateRequest;
import is.hi.team6.newsaggregator.service.ArticleImportService;
import is.hi.team6.newsaggregator.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final ArticleImportService imports;
    private final ArticleService articles;

    public AdminArticleController(
            ArticleImportService imports,
            ArticleService articles) {
        this.imports = imports;
        this.articles = articles;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ArticleSummary> importArticles(
            @Valid @RequestBody ArticleImportRequest request) {
        return imports.importArticles(request);
    }

    @PutMapping("/{id}")
    public ArticleResponse updateAnyArticle(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ArticleUpdateRequest request) {
        return articles.updateAnyArticle(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnyArticle(@PathVariable @Min(1) Long id) {
        articles.deleteAnyArticle(id);
    }
}
