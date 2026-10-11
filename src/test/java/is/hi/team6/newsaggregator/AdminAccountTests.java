package is.hi.team6.newsaggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import is.hi.team6.newsaggregator.dto.UpdateAccountRequest;
import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-account-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
class AdminAccountTests {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
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
    void createdAdminCanLogInAndManageAnotherAccount() throws Exception {
        mvc.perform(post("/api/admin/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"NewAdmin@Example.com","password":"admin-password123","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("newadmin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var admin = accounts.findByEmail("newadmin@example.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserAccount.Role.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo("admin-password123");
        assertThat(passwords.matches("admin-password123", admin.getPasswordHash())).isTrue();
        assertThat(accounts.count()).isEqualTo(2);

        TestSecurityContextHolder.clearContext();
        var login = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", "newadmin@example.com").param("password", "admin-password123"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("changed@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateAdminEmailDoesNotPromoteAnExistingUser() throws Exception {
        mvc.perform(post("/api/admin/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"READER@example.com","password":"admin-password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));

        assertThat(accounts.count()).isEqualTo(1);
        assertAccountUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"email\":\"invalid\",\"password\":\"admin-password123\"}",
            "{\"email\":\"admin@example.com\",\"password\":\"short\"}",
            "{\"email\":\"admin@example.com\",\"password\":\"        \"}"
    })
    void invalidAdminDetailsDoNotCreateAnAccount(String request) throws Exception {
        mvc.perform(post("/api/admin/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void regularUserCannotCreateAnAdmin() throws Exception {
        mvc.perform(post("/api/admin/accounts").with(user("reader@example.com").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"admin-password123"}
                                """))
                .andExpect(status().isForbidden());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void unauthenticatedUserCannotCreateAnAdmin() throws Exception {
        mvc.perform(post("/api/admin/accounts").with(anonymous()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"admin-password123"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void adminCreationRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"admin-password123"}
                                """))
                .andExpect(status().isForbidden());
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void adminCanUpdateAnotherAccountAndTheNewCredentialsWork() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Updated@Example.com","password":"new-password123","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var updated = accounts.findById(account.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).isNotEqualTo("new-password123");
        assertThat(passwords.matches("new-password123", updated.getPasswordHash())).isTrue();
        assertThat(accounts.count()).isEqualTo(1);

        mvc.perform(post("/api/auth/login").with(anonymous()).with(csrf())
                        .param("email", "updated@example.com").param("password", "new-password123"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(anonymous()).with(csrf())
                        .param("email", "updated@example.com").param("password", "password123"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(anonymous()).with(csrf())
                        .param("email", "reader@example.com").param("password", "password123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void omittingPasswordPreservesTheExistingHash() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"renamed@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("renamed@example.com"));

        assertThat(accounts.findById(account.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(account.getPasswordHash());
    }

    @Test
    void keepingTheSameEmailIsAllowed() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"READER@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("reader@example.com"));
    }

    @Test
    void duplicateEmailRollsBackBothEmailAndPasswordChanges() throws Exception {
        accounts.saveAndFlush(new UserAccount("taken@example.com", passwords.encode("other-password")));

        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"TAKEN@example.com","password":"new-password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));

        assertAccountUnchanged();
        assertThat(accounts.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"email\":\"invalid\"}", "{\"email\":\"\"}",
            "{\"email\":\"changed@example.com\",\"password\":\"short\"}",
            "{\"email\":\"changed@example.com\",\"password\":\"\"}",
            "{\"email\":\"changed@example.com\",\"password\":\"        \"}"
    })
    void invalidUpdatesLeaveTheAccountUnchanged(String request) throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertAccountUnchanged();
    }

    @Test
    void overlongPasswordIsRejected() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateAccountRequest("changed@example.com", "a".repeat(129)))))
                .andExpect(status().isBadRequest());
        assertAccountUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidAccountIdentifiersAreRejected(String id) throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isBadRequest());
        assertAccountUnchanged();
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", Long.MAX_VALUE).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void regularUserCannotUpdateAccounts() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId())
                        .with(user("other@example.com").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isForbidden());
        assertAccountUnchanged();
    }

    @Test
    void unauthenticatedUserCannotUpdateAccounts() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId()).with(anonymous()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isUnauthorized());
        assertAccountUnchanged();
    }

    @Test
    void adminUpdateRequiresCsrfProtection() throws Exception {
        mvc.perform(put("/api/admin/accounts/{id}", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@example.com"}
                                """))
                .andExpect(status().isForbidden());
        assertAccountUnchanged();
    }

    private void assertAccountUnchanged() {
        var unchanged = accounts.findById(account.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(account.getEmail());
        assertThat(unchanged.getPasswordHash()).isEqualTo(account.getPasswordHash());
        assertThat(unchanged.getRole()).isEqualTo(account.getRole());
    }
}
