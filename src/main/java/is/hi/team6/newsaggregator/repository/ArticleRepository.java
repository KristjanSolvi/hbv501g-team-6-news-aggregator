package is.hi.team6.newsaggregator.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.UserAccount;

public interface ArticleRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {

    @Override
    @EntityGraph(attributePaths = "source")
    Page<Article> findAll(Pageable pageable);

    @Modifying
    @Query("UPDATE Article a SET a.owner = NULL WHERE a.owner = :user")
    void removeOwnershipByUser(@Param("user") UserAccount user);
}

