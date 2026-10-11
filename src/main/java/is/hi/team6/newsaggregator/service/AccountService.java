package is.hi.team6.newsaggregator.service;

import org.springframework.security.core.userdetails.UserDetailsService;

import is.hi.team6.newsaggregator.dto.AccountResponse;
import is.hi.team6.newsaggregator.dto.RegistrationRequest;
import is.hi.team6.newsaggregator.dto.UpdateAccountRequest;

public interface AccountService extends UserDetailsService {

    AccountResponse register(RegistrationRequest request);

    AccountResponse createAdmin(RegistrationRequest request);

    AccountResponse updateOtherAccount(Long id, UpdateAccountRequest request);
}
