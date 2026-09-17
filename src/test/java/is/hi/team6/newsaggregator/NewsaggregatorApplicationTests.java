package is.hi.team6.newsaggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import is.hi.team6.newsaggregator.dto.RegistrationRequest;
import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.Favourite;
import is.hi.team6.newsaggregator.model.NewsSource;
import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NewsaggregatorApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entities;

    @Test
    void registrationStoresOnlyAPasswordHashAndCannotCreateAnAdmin() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Reader@Example.com","password":"password123","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("reader@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(unauthenticated());

        var account = accounts.findByEmail("reader@example.com").orElseThrow();
        assertThat(account.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwords.matches("password123", account.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailsAreRejectedIgnoringCase() throws Exception {
        createAccount();
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationRequest("READER@example.com", "another-password"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @ParameterizedTest
    @CsvSource({"invalid-email,password123", "reader@example.com,short", ",password123", "reader@example.com,"})
    void invalidRegistrationDoesNotCreateAnAccount(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationRequest(email, password))))
                .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void registrationRejectsOverlongPasswords() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationRequest("reader@example.com", "a".repeat(129)))))
                .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void csrfRegistrationAndLoginWorkTogetherAndRememberTheUser() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var csrfToken = json.readTree(result.getResponse().getContentAsString());
        String header = csrfToken.get("headerName").asString();
        String token = csrfToken.get("token").asString();
        String oldSessionId = session.getId();

        mvc.perform(post("/api/auth/register").session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegistrationRequest("reader@example.com", "password123"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "READER@example.com").param("password", "password123"))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername("reader@example.com"));

        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(get("/api/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("reader@example.com"));
    }

    @ParameterizedTest
    @CsvSource({"reader@example.com,wrong-password", "unknown@example.com,password123"})
    void invalidCredentialsReturnTheSameError(String email, String password) throws Exception {
        createAccount();
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login"})
    void authenticationPostsRequireCsrfProtection(String endpoint) throws Exception {
        mvc.perform(post(endpoint)).andExpect(status().isForbidden());
        assertThat(accounts.count()).isZero();
    }

    @Test
    void publicFeedIsPaginatedNewestFirstWithSourceInformation() throws Exception {
        var source = new NewsSource("Example News", "https://example.com");
        entities.persist(source);
        var owner = createAccount();
        createArticle("Oldest", "2026-09-01T12:00:00Z", source, owner);
        createArticle("Newer", "2026-09-02T12:00:00Z", source, owner);
        createArticle("Newest", "2026-09-03T12:00:00Z", source, owner);
        entities.flush();
        entities.clear();

        mvc.perform(get("/api/articles").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Newest"))
                .andExpect(jsonPath("$.content[1].title").value("Newer"))
                .andExpect(jsonPath("$.content[0].category").value("Science"))
                .andExpect(jsonPath("$.content[0].publishedAt").value("2026-09-03T12:00:00Z"))
                .andExpect(jsonPath("$.content[0].source.name").value("Example News"))
                .andExpect(jsonPath("$.content[0].owner").doesNotExist())
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/articles").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Oldest"))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void equalPublicationDatesHaveAStableOrderAndSourceIsOptional() throws Exception {
        createArticle("First", "2026-09-01T12:00:00Z", null, null);
        createArticle("Second", "2026-09-01T12:00:00Z", null, null);

        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Second"))
                .andExpect(jsonPath("$.content[1].title").value("First"))
                .andExpect(jsonPath("$.content[0].source").isEmpty());
    }

    @Test
    void emptyFeedAndPagesBeyondTheEndAreSuccessful() throws Exception {
        mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        createArticle("Only article", "2026-09-01T12:00:00Z", null, null);
        mvc.perform(get("/api/articles").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=abc", "size=1.5"})
    void invalidPaginationIsRejected(String query) throws Exception {
        mvc.perform(get("/api/articles?" + query)).andExpect(status().isBadRequest());
    }

    @Test
    void otherEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Login required"));
    }

    @Test
    void databasePreventsDuplicateFavourites() {
        var user = createAccount();
        var article = createArticle("Saved article", "2026-09-01T12:00:00Z", null, user);
        entities.persist(new Favourite(user, article));
        assertThatThrownBy(() -> entities.persist(new Favourite(user, article)))
                .isInstanceOf(PersistenceException.class);
    }

    private UserAccount createAccount() {
        return accounts.saveAndFlush(new UserAccount("reader@example.com", passwords.encode("password123")));
    }

    private Article createArticle(String title, String date, NewsSource source, UserAccount owner) {
        var article = new Article(title, "Article body", "https://example.com/article", "Science",
                Instant.parse(date), source, owner);
        entities.persist(article);
        return article;
    }

}
