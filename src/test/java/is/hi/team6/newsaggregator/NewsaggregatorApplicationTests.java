package is.hi.team6.newsaggregator;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void publicArticleIncludesContentAndSourceWithoutExposingAccountDetails() throws Exception {
        var source = sources.saveAndFlush(new NewsSource("Example News", "https://example.com"));
        var owner = createAccount();
        var article = createArticle("Full article", "2026-09-01T12:00:00Z", source, owner);
        entities.persist(new Favourite(owner, article));
        entities.flush();
        entities.clear();

        mvc.perform(get("/api/articles/{id}", article.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(article.getId()))
                .andExpect(jsonPath("$.title").value("Full article"))
                .andExpect(jsonPath("$.content").value("Article body"))
                .andExpect(jsonPath("$.category").value("Science"))
                .andExpect(jsonPath("$.publishedAt").value("2026-09-01T12:00:00Z"))
                .andExpect(jsonPath("$.url").value("https://example.com/article"))
                .andExpect(jsonPath("$.source.id").value(source.getId()))
                .andExpect(jsonPath("$.source.name").value("Example News"))
                .andExpect(jsonPath("$.source.url").value("https://example.com"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId()))
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
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidArticleIdentifiersAreRejected(String id) throws Exception {
        mvc.perform(get("/api/articles/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownArticleReturnsNotFound() throws Exception {
        mvc.perform(get("/api/articles/{id}", Long.MAX_VALUE))
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
    void databasePreventsDuplicateFavourites() {
        var user = createAccount();
        var article = createArticle("Saved article", "2026-09-01T12:00:00Z", null, user);
        entities.persist(new Favourite(user, article));
        assertThatThrownBy(() -> entities.persist(new Favourite(user, article)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
void adminCanImportArticlesFromApprovedSource() throws Exception {
    var source = sources.saveAndFlush(
            new NewsSource("Example News", "https://example.com"));

    String request = """
            {
              "sourceId": %d,
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
            .andExpect(jsonPath("$[0].source.id").value(source.getId()))
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
                              "sourceId": 1,
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
                              "sourceId": 999999,
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
                              "sourceId": 1,
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
                              "sourceId": 1,
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
                        .with(user("reader@example.com").roles("USER"))
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
                .andExpect(jsonPath("$.ownerId").value(account.getId()))
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
                "sourceId": %d
                }
                """.formatted(source.getId());

        mvc.perform(post("/api/articles")
                        .with(user("reader@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Article with source"))
                .andExpect(jsonPath("$.source.id").value(source.getId()))
                .andExpect(jsonPath("$.source.name").value("Example News"))
                .andExpect(jsonPath("$.ownerId").value(account.getId()));

        assertThat(articles.count()).isEqualTo(1);
        }


        @Test
        void createArticleRejectsUnknownNewsSource() throws Exception {
        createAccount();

        mvc.perform(post("/api/articles")
                        .with(user("reader@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                "title": "Article with invalid source",
                                "content": "Article body",
                                "category": "Science",
                                "publishedAt": "2026-10-10T12:00:00Z",
                                "sourceId": 999999
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
                Long id;
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
                id = json.get(0).get("id").longValue();
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
                Long id;
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
                id = json.get(0).get("id").longValue();
                mvc.perform(delete("/api/admin/newssources/"+id.toString())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(status().isNoContent());
                mvc.perform(get("/api/admin/newssources/sources")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())).andExpect(status().isOk())
                        .andExpect(jsonPath("$[?(@.id == " + id + ")]")
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

        @Test
        void ownerCanUpdateOwnArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(put("/api/articles/{id}", article.getId())
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Updated title",
                                        "content": "Updated body",
                                        "url": "https://example.com/updated",
                                        "category": "Technology",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Updated title"))
                        .andExpect(jsonPath("$.content").value("Updated body"))
                        .andExpect(jsonPath("$.category").value("Technology"))
                        .andExpect(jsonPath("$.ownerId").value(owner.getId()));

                entities.flush();
                entities.clear();

                var updated = articles.findById(article.getId()).orElseThrow();
                assertThat(updated.getTitle()).isEqualTo("Updated title");
                assertThat(updated.getContent()).isEqualTo("Updated body");
                assertThat(updated.getCategory()).isEqualTo("Technology");
        }

        @Test
        void userCannotUpdateAnotherUsersArticle() throws Exception {
                var owner = accounts.saveAndFlush(
                        new UserAccount("owner@example.com", passwords.encode("password123")));
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(put("/api/articles/{id}", article.getId())
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Hacked title",
                                        "content": "Changed body",
                                        "category": "Technology",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isForbidden());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(article.getId()).orElseThrow().getTitle())
                        .isEqualTo("Original title");
        }

        @Test
        void ownerCannotUpdateUnknownArticle() throws Exception {
                createAccount();

                mvc.perform(put("/api/articles/{id}", Long.MAX_VALUE)
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Updated title",
                                        "content": "Body",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isNotFound());
        }

        @Test
        void updateArticleRejectsBlankTitle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(put("/api/articles/{id}", article.getId())
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "",
                                        "content": "Updated body",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isBadRequest());

                assertThat(article.getTitle()).isEqualTo("Original title");
        }

        @Test
        void unauthenticatedUserCannotUpdateArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(put("/api/articles/{id}", article.getId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Unauthorized update",
                                        "content": "Body",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isUnauthorized());

                assertThat(article.getTitle()).isEqualTo("Original title");
        }

    
        @Test
        void ownerCanDeleteOwnArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("My article", "2026-10-10T12:00:00Z", null, owner);
                Long articleId = article.getId();

                mvc.perform(delete("/api/articles/{id}", articleId)
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf()))
                        .andExpect(status().isNoContent());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(articleId)).isEmpty();
        }

        @Test
        void userCannotDeleteAnotherUsersArticle() throws Exception {
                var owner = accounts.saveAndFlush(
                        new UserAccount("owner@example.com", passwords.encode("password123")));
                var article = createArticle("Protected article", "2026-10-10T12:00:00Z", null, owner);
                Long articleId = article.getId();

                mvc.perform(delete("/api/articles/{id}", articleId)
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf()))
                        .andExpect(status().isForbidden());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(articleId)).isPresent();
        }

        @Test
        void deletingArticleAlsoRemovesItsFavourites() throws Exception {
                var owner = createAccount();
                var article = createArticle("Saved article", "2026-10-10T12:00:00Z", null, owner);
                Long articleId = article.getId();

                entities.persist(new Favourite(owner, article));
                entities.flush();

                mvc.perform(delete("/api/articles/{id}", articleId)
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf()))
                        .andExpect(status().isNoContent());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(articleId)).isEmpty();
                assertThat(entities.createQuery(
                        "select count(f) from Favourite f", Long.class)
                        .getSingleResult()).isZero();
        }

        @Test
        void unauthenticatedUserCannotDeleteArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Protected article", "2026-10-10T12:00:00Z", null, owner);
                Long articleId = article.getId();

                mvc.perform(delete("/api/articles/{id}", articleId)
                                .with(csrf()))
                        .andExpect(status().isUnauthorized());

                assertThat(articles.findById(articleId)).isPresent();
        }

        @Test
        void deletingUnknownArticleReturnsNotFound() throws Exception {
                mvc.perform(delete("/api/articles/{id}", Long.MAX_VALUE)
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf()))
                        .andExpect(status().isNotFound());
        }

        @Test
        void adminCanUpdateAnotherUsersArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);
                Long id = article.getId();

                mvc.perform(put("/api/admin/articles/{id}", id)
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Admin updated",
                                        "content": "Updated by administrator",
                                        "category": "Technology",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Admin updated"))
                        .andExpect(jsonPath("$.ownerId").value(owner.getId()));

                entities.flush();
                entities.clear();

                assertThat(articles.findById(id).orElseThrow().getTitle())
                        .isEqualTo("Admin updated");
        }

        @Test
        void adminCanDeleteAnotherUsersArticle() throws Exception {
                var owner = createAccount();
                var article = createArticle("Protected article", "2026-10-10T12:00:00Z", null, owner);
                Long id = article.getId();

                mvc.perform(delete("/api/admin/articles/{id}", id)
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf()))
                        .andExpect(status().isNoContent());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(id)).isEmpty();
        }

        @Test
        void regularUserCannotUpdateArticleThroughAdminEndpoint() throws Exception {
                var owner = createAccount();
                var article = createArticle("Original title", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(put("/api/admin/articles/{id}", article.getId())
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Unauthorized change",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isForbidden());

                assertThat(article.getTitle()).isEqualTo("Original title");
        }

        @Test
        void regularUserCannotDeleteArticleThroughAdminEndpoint() throws Exception {
                var owner = createAccount();
                var article = createArticle("Protected article", "2026-10-10T12:00:00Z", null, owner);

                mvc.perform(delete("/api/admin/articles/{id}", article.getId())
                                .with(user("reader@example.com").roles("USER"))
                                .with(csrf()))
                        .andExpect(status().isForbidden());

                assertThat(articles.findById(article.getId())).isPresent();
        }

        @Test
        void adminCanUpdateArticleWithoutOwner() throws Exception {
                var article = createArticle("Imported article", "2026-10-10T12:00:00Z", null, null);

                mvc.perform(put("/api/admin/articles/{id}", article.getId())
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Corrected imported article",
                                        "category": "News",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Corrected imported article"))
                        .andExpect(jsonPath("$.ownerId").isEmpty());
        }

        @Test
        void adminCanDeleteArticleWithoutOwner() throws Exception {
                var article = createArticle("Imported article", "2026-10-10T12:00:00Z", null, null);
                Long id = article.getId();

                mvc.perform(delete("/api/admin/articles/{id}", id)
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf()))
                        .andExpect(status().isNoContent());

                entities.flush();
                entities.clear();

                assertThat(articles.findById(id)).isEmpty();
        }

        @Test
        void adminCannotUpdateUnknownArticle() throws Exception {
                mvc.perform(put("/api/admin/articles/{id}", Long.MAX_VALUE)
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Updated",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isNotFound());
        }

        @Test
        void adminCannotDeleteUnknownArticle() throws Exception {
                mvc.perform(delete("/api/admin/articles/{id}", Long.MAX_VALUE)
                                .with(user("admin@example.com").roles("ADMIN"))
                                .with(csrf()))
                        .andExpect(status().isNotFound());
        }

        @Test
        void unauthenticatedUserCannotUseAdminArticleEndpoints() throws Exception {
                mvc.perform(delete("/api/admin/articles/{id}", 1L)
                                .with(csrf()))
                        .andExpect(status().isUnauthorized());

                mvc.perform(put("/api/admin/articles/{id}", 1L)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                        "title": "Updated",
                                        "category": "Science",
                                        "publishedAt": "2026-10-20T12:00:00Z"
                                        }
                                        """))
                        .andExpect(status().isUnauthorized());
        }
}
