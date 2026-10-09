package is.hi.team6.newsaggregator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import is.hi.team6.newsaggregator.model.NewsSource;

public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
}