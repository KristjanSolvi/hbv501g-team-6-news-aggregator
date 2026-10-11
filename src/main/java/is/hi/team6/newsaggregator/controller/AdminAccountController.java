package is.hi.team6.newsaggregator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import is.hi.team6.newsaggregator.dto.AccountResponse;
import is.hi.team6.newsaggregator.dto.RegistrationRequest;
import is.hi.team6.newsaggregator.dto.UpdateAccountRequest;
import is.hi.team6.newsaggregator.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountService accounts;

    public AdminAccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAdmin(@Valid @RequestBody RegistrationRequest request) {
        return accounts.createAdmin(request);
    }

    @PutMapping("/{id}")
    public AccountResponse updateAccount(@PathVariable @Min(1) Long id,
                                         @Valid @RequestBody UpdateAccountRequest request) {
        return accounts.updateOtherAccount(id, request);
    }
}
