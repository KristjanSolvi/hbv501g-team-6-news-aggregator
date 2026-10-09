package is.hi.team6.newsaggregator.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import is.hi.team6.newsaggregator.dto.ArticleImportRequest;
import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.NewsSourceRepository;

@Service
public class ArticleImportServiceImpl implements ArticleImportService {

    private final ArticleRepository articles;
    private final NewsSourceRepository sources;

    public ArticleImportServiceImpl(
            ArticleRepository articles,
            NewsSourceRepository sources) {
        this.articles = articles;
        this.sources = sources;
    }

    @Override
    @Transactional
    public List<ArticleSummary> importArticles(ArticleImportRequest request) {

        var source = sources.findById(request.sourceId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "News source not found"));

        var importedArticles = request.articles().stream()
                .map(item -> new Article(
                        item.title(),
                        item.content(),
                        item.url(),
                        item.category(),
                        item.publishedAt(),
                        source,
                        null
                ))
                .toList();

        return articles.saveAll(importedArticles).stream()
                .map(ArticleSummary::from)
                .toList();
    }
}