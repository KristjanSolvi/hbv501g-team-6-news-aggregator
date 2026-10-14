package is.hi.team6.newsaggregator.service;

import java.util.List;

import is.hi.team6.newsaggregator.dto.ArticleImportRequest;
import is.hi.team6.newsaggregator.dto.ArticleSummary;

public interface ArticleImportService {

    List<ArticleSummary> importArticles(ArticleImportRequest request);
}