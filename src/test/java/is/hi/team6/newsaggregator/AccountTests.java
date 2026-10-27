package is.hi.team6.newsaggregator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountTests {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;

    UserAccount account;
    UserAccount other;

    @BeforeEach
    void createAccounts() {
        account = accounts.saveAndFlush(new UserAccount("reader@example.com", passwords.encode("password123")));
        other = accounts.saveAndFlush(new UserAccount("other@example.com", passwords.encode("other-password")));
    }

    @AfterEach
    void deleteAccounts() {
        accounts.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void signedInUserCanViewOnlyTheirOwnAccount(String role) throws Exception {
        account.changeRole(UserAccount.Role.valueOf(role));
        accounts.saveAndFlush(account);
        var session = login(account.getEmail(), "password123");

        mvc.perform(get("/api/accounts/me").session(session)
                        .param("id", other.getId().toString()).param("email", other.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.email").value(account.getEmail()))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void viewingAccountRequiresLogin() throws Exception {
        mvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingAccountReturnsNotFound() throws Exception {
        var session = login(account.getEmail(), "password123");
        accounts.deleteById(account.getId());

        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
