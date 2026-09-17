package is.hi.team6.newsaggregator.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import is.hi.team6.newsaggregator.model.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
}
