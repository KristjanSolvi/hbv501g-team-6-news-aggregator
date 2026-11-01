package is.hi.team6.newsaggregator.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.AccountResponse;
import is.hi.team6.newsaggregator.dto.UpdateAccountRequest;
import is.hi.team6.newsaggregator.service.AccountService;
import is.hi.team6.newsaggregator.service.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts/me")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public AccountResponse getOwnAccount(@AuthenticationPrincipal AccountUserDetails principal) {
        return accounts.getOwnAccount(principal.getAccountId());
    }

    @PutMapping
    public AccountResponse updateOwnAccount(@Valid @RequestBody UpdateAccountRequest request,
                                           @AuthenticationPrincipal AccountUserDetails principal,
                                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var account = accounts.updateOwnAccount(principal.getAccountId(), request);
        if (!account.email().equals(principal.getUsername())) {
            var updated = new AccountUserDetails(account.id(), account.email(), "", account.role());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    updated, null, updated.getAuthorities()));
            SecurityContextHolder.setContext(context);
            new HttpSessionSecurityContextRepository().saveContext(context, httpRequest, httpResponse);
        }
        return account;
    }

    @DeleteMapping 
    public void deleteOwnAccount(@AuthenticationPrincipal AccountUserDetails principal){
        accounts.deleteOwnAccount(accounts.getOwnAccount(
                                    principal.getAccountId())
                                    .email());
    }
}
