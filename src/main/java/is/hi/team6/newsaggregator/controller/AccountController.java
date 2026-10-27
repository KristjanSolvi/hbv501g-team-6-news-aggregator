package is.hi.team6.newsaggregator.controller;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.AccountResponse;
import is.hi.team6.newsaggregator.service.AccountService;

@RestController
@RequestMapping("/api/accounts/me")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public AccountResponse getOwnAccount(Principal principal) {
        return accounts.getOwnAccount(principal.getName());
    }
}
