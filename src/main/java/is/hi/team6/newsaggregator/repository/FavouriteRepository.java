package is.hi.team6.newsaggregator.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.Favourite;
import is.hi.team6.newsaggregator.model.UserAccount;

public interface FavouriteRepository extends JpaRepository<Favourite, UUID> {

    boolean existsByUserAndArticle(
            UserAccount user,
            Article article
    );

    @EntityGraph(attributePaths = {"article", "article.source"})
    Page<Favourite> findByUser(
            UserAccount user,
            Pageable pageable
    );

    Optional<Favourite> findByUserAndArticle(
            UserAccount user,
            Article article
    );
}