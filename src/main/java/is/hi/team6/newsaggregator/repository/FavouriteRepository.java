package is.hi.team6.newsaggregator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.Favourite;

public interface FavouriteRepository extends JpaRepository<Favourite, Long> {

    void deleteByArticle(Article article);
}