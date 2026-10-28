package is.hi.team6.newsaggregator.service;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import is.hi.team6.newsaggregator.model.UserAccount;

public class AccountUserDetails extends User {

    private final Long accountId;

    public AccountUserDetails(Long accountId, String email, String password, UserAccount.Role role) {
        super(email, password, AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
