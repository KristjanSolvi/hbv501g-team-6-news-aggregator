package is.hi.team6.newsaggregator;

import java.util.UUID;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import is.hi.team6.newsaggregator.dto.RegistrationRequest;
import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.Favourite;
import is.hi.team6.newsaggregator.model.NewsSource;
import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.NewsSourceRepository;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;
import is.hi.team6.newsaggregator.service.AccountService;
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
    @Autowired NewsSourceRepository sources;
    @Autowired ArticleRepository articles;
    @Autowired AccountService accountService;

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
        var first = createArticle("First", "2026-09-01T12:00:00Z", null, null);
        var second = createArticle("Second", "2026-09-01T12:00:00Z", null, null);

        var response = mvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                        first.getId().toString(), second.getId().toString())))
                .andExpect(jsonPath("$.content[0].source").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(get("/api/articles")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo(response);
    }

    @Test
    void publicArticleIncludesContentAndSourceWithoutExposingAccountDetails() throws Exception {
        var source = sources.saveAndFlush(new NewsSource("Example News", "https://example.com"));
        var owner = createAccount();
        var article = createArticle("Full article", "2026-09-01T12:00:00Z", source, owner);
        entities.persist(new Favourite(owner, article));
        entities.flush();
        entities.clear();

        mvc.perform(get("/api/articles/{id}", article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(article.getId().toString()))
                .andExpect(jsonPath("$.title").value("Full article"))
                .andExpect(jsonPath("$.content").value("Article body"))
                .andExpect(jsonPath("$.category").value("Science"))
                .andExpect(jsonPath("$.publishedAt").value("2026-09-01T12:00:00Z"))
                .andExpect(jsonPath("$.url").value("https://example.com/article"))
                .andExpect(jsonPath("$.source.id").value(source.getId().toString()))
                .andExpect(jsonPath("$.source.name").value("Example News"))
                .andExpect(jsonPath("$.source.url").value("https://example.com"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(articles.count()).isEqualTo(1);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(entities.createQuery("select count(f) from Favourite f", Long.class).getSingleResult())
                .isEqualTo(1);
    }

    @Test
    void authenticatedReaderCanViewArticleWithoutSourceOrOwner() throws Exception {
        var article = createArticle("Standalone article", "2026-09-01T12:00:00Z", null, null);

        mvc.perform(get("/api/articles/{id}", article.getId()).with(user("reader@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Standalone article"))
                .andExpect(jsonPath("$.source").isEmpty())
                .andExpect(jsonPath("$.ownerId").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidArticleIdentifiersAreRejected(String id) throws Exception {
        mvc.perform(get("/api/articles/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownArticleReturnsNotFound() throws Exception {
        mvc.perform(get("/api/articles/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Article not found"));
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
    void identifiersAreRandomUuidsAndRelationshipsSurviveReloading() {
        var source = sources.saveAndFlush(new NewsSource("UUID source", "https://example.com"));
        var account = createAccount();
        var article = createArticle("UUID article", "2026-10-01T12:00:00Z", source, account);
        var favourite = new Favourite(account, article);
        entities.persist(favourite);
        entities.flush();
        entities.clear();

        var ids = java.util.List.of(source.getId(), account.getId(), article.getId(), favourite.getId());
        assertThat(ids).doesNotHaveDuplicates();
        for (var id : ids) {
            assertThat(id.version()).isEqualTo(4);
            assertThat(id.variant()).isEqualTo(2);
        }
        var saved = entities.find(Favourite.class, favourite.getId());
        assertThat(saved.getUser().getId()).isEqualTo(account.getId());
        assertThat(saved.getArticle().getId()).isEqualTo(article.getId());
        assertThat(saved.getArticle().getSource().getId()).isEqualTo(source.getId());
        assertThat(saved.getArticle().getOwner().getId()).isEqualTo(account.getId());
    }

    @Test
    void databasePreventsDuplicateFavourites() {
        var user = createAccount();
        var article = createArticle("Saved article", "2026-09-01T12:00:00Z", null, user);
        entities.persist(new Favourite(user, article));
        assertThatThrownBy(() -> {
            entities.persist(new Favourite(user, article));
            entities.flush();
        })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
void adminCanImportArticlesFromApprovedSource() throws Exception {
    var source = sources.saveAndFlush(
            new NewsSource("Example News", "https://example.com"));

    String request = """
            {
              "sourceId": "%s",
              "articles": [
                {
                  "title": "Imported article",
                  "content": "Imported article body",
                  "url": "https://example.com/article/1",
                  "category": "Science",
                  "publishedAt": "2026-10-09T12:00:00Z"
                }
              ]
            }
            """.formatted(source.getId());

    mvc.perform(post("/api/admin/articles/import")
                    .with(user("admin@example.com").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$[0].title").value("Imported article"))
            .andExpect(jsonPath("$[0].source.id").value(source.getId().toString()))
            .andExpect(jsonPath("$[0].source.name").value("Example News"));

    assertThat(articles.count()).isEqualTo(1);
}

@Test
void regularUserCannotImportArticles() throws Exception {
    mvc.perform(post("/api/admin/articles/import")
                    .with(user("reader@example.com").roles("USER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "sourceId": "9491db8b-175a-4920-9ee8-d3fb454398c0",
                              "articles": [
                                {
                                  "title": "Article",
                                  "category": "News",
                                  "publishedAt": "2026-10-09T12:00:00Z"
                                }
                              ]
                            }
                            """))
            .andExpect(status().isForbidden());
}

@Test
void importRejectsUnknownNewsSource() throws Exception {
    mvc.perform(post("/api/admin/articles/import")
                    .with(user("admin@example.com").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "sourceId": "9491db8b-175a-4920-9ee8-d3fb454398c0",
                              "articles": [
                                {
                                  "title": "Article",
                                  "category": "News",
                                  "publishedAt": "2026-10-09T12:00:00Z"
                                }
                              ]
                            }
                            """))
            .andExpect(status().isNotFound());
}

@Test
void importRejectsEmptyArticleList() throws Exception {
    mvc.perform(post("/api/admin/articles/import")
                    .with(user("admin@example.com").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "sourceId": "9491db8b-175a-4920-9ee8-d3fb454398c0",
                              "articles": []
                            }
                            """))
            .andExpect(status().isBadRequest());
}

@Test
void unauthenticatedUserCannotImportArticles() throws Exception {
    mvc.perform(post("/api/admin/articles/import")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "sourceId": "9491db8b-175a-4920-9ee8-d3fb454398c0",
                              "articles": [
                                {
                                  "title": "Imported article",
                                  "content": "Article body",
                                  "url": "https://example.com/article",
                                  "category": "Science",
                                  "publishedAt": "2026-10-09T12:00:00Z"
                                }
                              ]
                            }
                            """))
            .andExpect(status().isUnauthorized());
}



        @Test
        void authenticatedUserCanCreateArticleWithoutSource() throws Exception {
        var account = createAccount();

        mvc.perform(post("/api/articles")
                        .with(user(accountService.loadUserByUsername("reader@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "title": "My first article",
                                "content": "This is the article body",
                                "url": "https://example.com/my-article",
                                "category": "Technology",
                                "publishedAt": "2026-10-10T12:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My first article"))
                .andExpect(jsonPath("$.content").value("This is the article body"))
                .andExpect(jsonPath("$.category").value("Technology"))
                .andExpect(jsonPath("$.ownerId").value(account.getId().toString()))
                .andExpect(jsonPath("$.source").value(
                        org.hamcrest.Matchers.nullValue()));

        assertThat(articles.count()).isEqualTo(1);
        }


        @Test
        void authenticatedUserCanCreateArticleWithSource() throws Exception {
        var account = createAccount();
        var source = sources.saveAndFlush(
                new NewsSource("Example News", "https://example.com"));

        String request = """
                {
                "title": "Article with source",
                "content": "Article body",
                "url": "https://example.com/article",
                "category": "Science",
                "publishedAt": "2026-10-10T12:00:00Z",
                "sourceId": "%s"
                }
                """.formatted(source.getId());

        mvc.perform(post("/api/articles")
                        .with(user(accountService.loadUserByUsername("reader@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Article with source"))
                .andExpect(jsonPath("$.source.id").value(source.getId().toString()))
                .andExpect(jsonPath("$.source.name").value("Example News"))
                .andExpect(jsonPath("$.ownerId").value(account.getId().toString()));

        assertThat(articles.count()).isEqualTo(1);
        }


        @Test
        void createArticleRejectsUnknownNewsSource() throws Exception {
        createAccount();

        mvc.perform(post("/api/articles")
                        .with(user(accountService.loadUserByUsername("reader@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "title": "Article with invalid source",
                                "content": "Article body",
                                "category": "Science",
                                "publishedAt": "2026-10-10T12:00:00Z",
                                "sourceId": "9491db8b-175a-4920-9ee8-d3fb454398c0"
                                }
                                """))
                .andExpect(status().isNotFound());

        assertThat(articles.count()).isZero();
        }


        @Test
        void unauthenticatedUserCannotCreateArticle() throws Exception {
        mvc.perform(post("/api/articles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "title": "Unauthorized article",
                                "category": "Science",
                                "publishedAt": "2026-10-10T12:00:00Z"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(articles.count()).isZero();
        }


        @Test
        void searchArticlesByKeyword() throws Exception {
        var account = createAccount();

        createArticle("Football news", "2026-10-10T12:00:00Z", null, account);
        createArticle("Technology update", "2026-10-09T12:00:00Z", null, account);

        entities.flush();

        mvc.perform(get("/api/articles/search")
                        .param("keyword", "football"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Football news"));
        }


        @Test
        void searchArticlesByCategory() throws Exception {
        var account = createAccount();

        createArticle("Science article", "2026-10-10T12:00:00Z", null, account);
        entities.flush();

        mvc.perform(get("/api/articles/search")
                        .param("category", "Science"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Science article"));

        mvc.perform(get("/api/articles/search")
                        .param("category", "Sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        void searchArticlesBySource() throws Exception {
        var account = createAccount();
        var source = sources.saveAndFlush(
                new NewsSource("Example News", "https://example.com"));

        createArticle("From source", "2026-10-10T12:00:00Z", source, account);
        createArticle("No source", "2026-10-09T12:00:00Z", null, account);
        entities.flush();

        mvc.perform(get("/api/articles/search")
                        .param("sourceId", source.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("From source"));
        }

        @Test
        void searchArticlesByDateRange() throws Exception {
        var account = createAccount();

        createArticle("Too old", "2026-10-01T12:00:00Z", null, account);
        createArticle("Within range", "2026-10-10T12:00:00Z", null, account);
        createArticle("Too new", "2026-10-20T12:00:00Z", null, account);
        entities.flush();

        mvc.perform(get("/api/articles/search")
                        .param("from", "2026-10-05T00:00:00Z")
                        .param("to", "2026-10-15T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Within range"));
        }
        @Test 
        void createNewsSource() throws Exception {
        mvc.perform(post("/api/admin/newssources/create")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "name": "Miss Information",
                                "url": "https://ms.information"
                                }""")).andExpect(status().isCreated());
        }

        @Test 
        void createNewsSourceInvalidUser() throws Exception {
        mvc.perform(post("/api/admin/newssources/create")
                        .with(user("USER@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "name": "Miss Information",
                                "url": "https://ms.information"
                                }""")).andExpect(status().is4xxClientError());
        }

        @Test 
        void getSources() throws Exception{
                mvc.perform(post("/api/admin/newssources/create")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "name": "Miss Information",
                                "url": "https://ms.information"
                                }""")).andExpect(status().isCreated());
                mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Miss Information"));
        }

        @Test 
        void updateSources() throws Exception{
                UUID id;
                mvc.perform(post("/api/admin/newssources/create")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "name": "Miss Information",
                                "url": "https://ms.information"
                                }""")).andExpect(status().isCreated());
                MvcResult response = mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].name")
                        .value("Miss Information"))
                        .andReturn();
                var jsonMapper = JsonMapper.builder().build();
                var json = jsonMapper.readTree(response.getResponse().getContentAsString());
                id = UUID.fromString(json.get(0).get("id").asString());
                mvc.perform(put("/api/admin/newssources/"+id.toString())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                        "name":"Moss Information",
                        "url":"https://ms.information"                
                        }        
                        """)).andExpect(status().isOk());
                mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].name")
                        .value("Moss Information"));     
}
        
        @Test 
        void deleteSource() throws Exception{
                UUID id;
                mvc.perform(post("/api/admin/newssources/create")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "name": "Miss Information",
                                "url": "https://ms.information"
                                }""")).andExpect(status().isCreated());
                MvcResult response = mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].name")
                        .value("Miss Information"))
                        .andReturn();
                var jsonMapper = JsonMapper.builder().build();
                var json = jsonMapper.readTree(response.getResponse().getContentAsString());
                id = UUID.fromString(json.get(0).get("id").asString());
                mvc.perform(delete("/api/admin/newssources/"+id.toString())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(status().isNoContent());
                mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.id == '" + id + "')]")
                        .doesNotExist());     
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
