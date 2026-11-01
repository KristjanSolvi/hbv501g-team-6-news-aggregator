package is.hi.team6.newsaggregator.service;

import java.util.Locale;
import java.util.UUID;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
    public AccountResponse getOwnAccount(UUID id) {
        return AccountResponse.from(findAccount(id));
    }

    @Override
    @Transactional
    public AccountResponse updateOwnAccount(UUID id, UpdateAccountRequest request) {
        return updateAccount(findAccount(id), request);
    }

    @Override
    public AccountResponse createAdmin(RegistrationRequest request) {
        var account = new UserAccount(normalizeEmail(request.email()), passwords.encode(request.password()));
        account.changeRole(UserAccount.Role.ADMIN);
        return saveAccount(account);
    }

    @Override
    @Transactional
    public AccountResponse updateOtherAccount(UUID id, UpdateAccountRequest request) {
        return updateAccount(findAccount(id), request);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        return new AccountUserDetails(account.getId(), account.getEmail(), account.getPasswordHash(), account.getRole());
    }

    private UserAccount findAccount(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private AccountResponse updateAccount(UserAccount account, UpdateAccountRequest request) {
        account.changeEmail(normalizeEmail(request.email()));
        if (request.password() != null) {
            account.changePasswordHash(passwords.encode(request.password()));
        }
        return saveAccount(account);
    }

    private AccountResponse saveAccount(UserAccount account) {
        try {
            return AccountResponse.from(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Override 
    @Transactional 
    public void deleteOwnAccount(String email){
        Optional<UserAccount> account = accounts.findByEmail(email);
        if (account.isEmpty()) {
            throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Missing User Account");         
        };
        accounts.delete(account.get());
        
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
