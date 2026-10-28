package is.hi.team6.newsaggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingAccountReturnsNotFound(boolean reuseEmail) throws Exception {
        var session = login(account.getEmail(), "password123");
        accounts.deleteById(account.getId());
        if (reuseEmail) {
            accounts.saveAndFlush(new UserAccount(account.getEmail(), passwords.encode("replacement-password")));
        }

        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void updatingOwnAccountPreservesIdentityAndRole(String role) throws Exception {
        account.changeRole(UserAccount.Role.valueOf(role));
        accounts.saveAndFlush(account);
        var session = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .param("id", other.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Updated@Example.com","password":"new-password123","role":"ADMIN","id":%d}
                                """.formatted(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(passwords.matches("new-password123", updated.getPasswordHash())).isTrue();
        assertThat(updated.getPasswordHash()).isNotEqualTo("new-password123");
        assertThat(updated.getRole()).isEqualTo(account.getRole());
        assertUnchanged(other);

        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.email").value("updated@example.com"));
        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"UPDATED@example.com"}
                                """))
                .andExpect(status().isOk());

        login("updated@example.com", "new-password123");
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", account.getEmail()).param("password", "password123"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "updated@example.com").param("password", "password123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherSessionStillUsesTheSameAccountAfterEmailIsReused() throws Exception {
        var session = login(account.getEmail(), "password123");
        var otherSession = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isOk());
        var replacement = accounts.saveAndFlush(
                new UserAccount(account.getEmail(), passwords.encode("replacement-password")));

        mvc.perform(get("/api/accounts/me").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.email").value("changed@example.com"));
        mvc.perform(put("/api/accounts/me").session(otherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed-again@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()));
        assertUnchanged(replacement);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"email\":\"changed@example.com\"}",
            "{\"email\":\"changed@example.com\",\"password\":null}"})
    void omittedOrNullPasswordPreservesTheHash(String request) throws Exception {
        var session = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("changed@example.com"));

        assertThat(accounts.findById(account.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(account.getPasswordHash());
    }

    @Test
    void passwordCanChangeWithoutChangingEmail() throws Exception {
        var session = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reader@example.com","password":"new-password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.getEmail()));

        login(account.getEmail(), "new-password123");
        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateEmailRollsBackChangesAndPreservesTheSession() throws Exception {
        var session = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"OTHER@example.com","password":"new-password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));

        assertUnchanged(account);
        assertUnchanged(other);
        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.getEmail()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalid\"}",
            "{\"email\":\"changed@example.com\",\"password\":\"short\"}",
            "{\"email\":\"changed@example.com\",\"password\":\"        \"}"})
    void invalidUpdatesLeaveTheAccountUnchanged(String request) throws Exception {
        var session = login(account.getEmail(), "password123");

        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());

        assertUnchanged(account);
    }

    @Test
    void updatingAccountRequiresLogin() throws Exception {
        mvc.perform(put("/api/accounts/me").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isUnauthorized());

        assertUnchanged(account);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInvalidCsrfTokenPreventsUpdates(boolean invalidToken) throws Exception {
        var session = login(account.getEmail(), "password123");
        var request = put("/api/accounts/me").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"changed@example.com"}
                        """);
        if (invalidToken) {
            request.with(csrf().useInvalidToken());
        }

        mvc.perform(request).andExpect(status().isForbidden());
        assertUnchanged(account);
    }

    private void assertUnchanged(UserAccount original) {
        var saved = accounts.findById(original.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(original.getEmail());
        assertThat(saved.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(saved.getRole()).isEqualTo(original.getRole());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
