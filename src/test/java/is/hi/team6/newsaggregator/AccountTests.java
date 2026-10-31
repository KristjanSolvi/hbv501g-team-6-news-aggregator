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

    @BeforeEach
    void createAccount() {
        account = accounts.saveAndFlush(new UserAccount("reader@example.com", passwords.encode("password123")));
    }

    @AfterEach
    void deleteAccounts() {
        accounts.deleteAll();
    }

    @Test
    void viewOwnAccount() throws Exception {
        mvc.perform(get("/api/accounts/me").session(login(account.getEmail(), "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.email").value(account.getEmail()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updateOwnAccount() throws Exception {
        var session = login(account.getEmail(), "password123");
        var otherSession = login(account.getEmail(), "password123");
        mvc.perform(put("/api/accounts/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Updated@Example.com","password":"new-password123","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        var updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(passwords.matches("new-password123", updated.getPasswordHash())).isTrue();
        mvc.perform(get("/api/accounts/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()));

        accounts.saveAndFlush(new UserAccount(account.getEmail(), passwords.encode("replacement-password")));
        mvc.perform(get("/api/accounts/me").session(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()));
        login("updated@example.com", "new-password123");
    }

    @Test
    void duplicateEmailLeavesAccountUnchanged() throws Exception {
        accounts.saveAndFlush(new UserAccount("taken@example.com", passwords.encode("other-password")));
        mvc.perform(put("/api/accounts/me").session(login(account.getEmail(), "password123")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"TAKEN@example.com","password":"new-password123"}
                                """))
                .andExpect(status().isConflict());

        var unchanged = accounts.findById(account.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(account.getEmail());
        assertThat(unchanged.getPasswordHash()).isEqualTo(account.getPasswordHash());
    }

    @Test
    void accountRequiresLoginAndCsrf() throws Exception {
        String body = "{\"email\":\"changed@example.com\"}";
        mvc.perform(get("/api/accounts/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/accounts/me").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/accounts/me").session(login(account.getEmail(), "password123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
