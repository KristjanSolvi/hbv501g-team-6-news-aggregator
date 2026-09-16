package is.hi.team6.newsaggregator.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import is.hi.team6.newsaggregator.dto.ArticleSummary;
import is.hi.team6.newsaggregator.repository.ArticleRepository;

@Service
public class ArticleService {

    private final ArticleRepository articles;

    public ArticleService(ArticleRepository articles) {
        this.articles = articles;
    }

    @Transactional(readOnly = true)
    public Page<ArticleSummary> getFeed(int page, int size) {
        var order = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
        return articles.findAll(PageRequest.of(page, size, order)).map(ArticleSummary::from);
    }
}
