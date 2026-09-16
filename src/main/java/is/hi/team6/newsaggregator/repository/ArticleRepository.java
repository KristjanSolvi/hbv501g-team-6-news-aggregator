package is.hi.team6.newsaggregator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import is.hi.team6.newsaggregator.model.Article;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Override
    @EntityGraph(attributePaths = "source")
    Page<Article> findAll(Pageable pageable);
}
