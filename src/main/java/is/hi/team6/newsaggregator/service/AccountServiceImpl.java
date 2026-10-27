package is.hi.team6.newsaggregator.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import is.hi.team6.newsaggregator.dto.AccountResponse;
import is.hi.team6.newsaggregator.dto.RegistrationRequest;
import is.hi.team6.newsaggregator.dto.UpdateAccountRequest;
import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@Service
public class AccountServiceImpl implements AccountService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;

    public AccountServiceImpl(UserAccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Override
    public AccountResponse register(RegistrationRequest request) {
        var account = new UserAccount(normalizeEmail(request.email()), passwords.encode(request.password()));
        return saveAccount(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getOwnAccount(String email) {
        var account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return AccountResponse.from(account);
    }

    @Override
    public AccountResponse createAdmin(RegistrationRequest request) {
        var account = new UserAccount(normalizeEmail(request.email()), passwords.encode(request.password()));
        account.changeRole(UserAccount.Role.ADMIN);
        return saveAccount(account);
    }

    @Override
    @Transactional
    public AccountResponse updateOtherAccount(Long id, UpdateAccountRequest request) {
        var account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.changeEmail(normalizeEmail(request.email()));
        if (request.password() != null) {
            account.changePasswordHash(passwords.encode(request.password()));
        }
        return saveAccount(account);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash()).roles(account.getRole().name()).build();
    }

    private AccountResponse saveAccount(UserAccount account) {
        try {
            return AccountResponse.from(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
