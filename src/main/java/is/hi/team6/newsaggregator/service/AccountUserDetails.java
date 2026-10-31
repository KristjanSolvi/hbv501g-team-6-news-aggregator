package is.hi.team6.newsaggregator.service;

import java.util.UUID;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import is.hi.team6.newsaggregator.model.UserAccount;

public class AccountUserDetails extends User {

    private final UUID accountId;

    public AccountUserDetails(UUID accountId, String email, String password, UserAccount.Role role) {
        super(email, password, AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
