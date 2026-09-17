package is.hi.team6.newsaggregator.dto;

import is.hi.team6.newsaggregator.model.UserAccount;

public record AccountResponse(Long id, String email, UserAccount.Role role) {

    public static AccountResponse from(UserAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getRole());
    }
}
