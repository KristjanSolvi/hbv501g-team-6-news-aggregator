package is.hi.team6.newsaggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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

import is.hi.team6.newsaggregator.model.Article;
import is.hi.team6.newsaggregator.model.Favourite;
import is.hi.team6.newsaggregator.model.UserAccount;
import is.hi.team6.newsaggregator.repository.ArticleRepository;
import is.hi.team6.newsaggregator.repository.FavouriteRepository;
import is.hi.team6.newsaggregator.repository.UserAccountRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:favourites-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FavouriteTests {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired ArticleRepository articles;
    @Autowired FavouriteRepository favourites;
    @Autowired PasswordEncoder passwords;

    UserAccount reader;
    Article first;
    Article second;

    @BeforeEach
    void setUp() {
        reader = accounts.saveAndFlush(new UserAccount("reader@example.com", passwords.encode("password123")));
        first = articles.saveAndFlush(new Article("First", null, null, "News",
                Instant.parse("2026-10-01T00:00:00Z"), null, null));
        second = articles.saveAndFlush(new Article("Second", null, null, "News",
                Instant.parse("2026-10-02T00:00:00Z"), null, null));
    }

    @AfterEach
    void cleanUp() {
        favourites.deleteAll();
        articles.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void saveAndListFavourites() throws Exception {
        var session = login(reader.getEmail());
        mvc.perform(post("/api/favourites/{id}", second.getId()).session(session).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favourites/{id}", first.getId()).session(session).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favourites/{id}", first.getId()).session(session).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(favourites.count()).isEqualTo(2);
        mvc.perform(get("/api/favourites?size=1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        accounts.saveAndFlush(new UserAccount("other@example.com", passwords.encode("password123")));
        mvc.perform(get("/api/favourites").session(login("other@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void emailChangeKeepsFavouritesWithTheirOwner() throws Exception {
        var session = login(reader.getEmail());
        favourites.saveAndFlush(new Favourite(reader, first));
        mvc.perform(put("/api/accounts/me").session(login(reader.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"renamed@example.com\"}"))
                .andExpect(status().isOk());
        var replacement = accounts.saveAndFlush(
                new UserAccount(reader.getEmail(), passwords.encode("replacement-password")));
        favourites.saveAndFlush(new Favourite(replacement, second));

        mvc.perform(get("/api/favourites").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()));
        mvc.perform(post("/api/favourites/{id}", second.getId()).session(session).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(favourites.existsByUserAndArticle(reader, second)).isTrue();
        mvc.perform(post("/api/articles").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Owned article","category":"News","publishedAt":"2026-10-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(reader.getId().toString()));
    }

    @Test
    void favouritesRequireLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/favourites")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/favourites/{id}", first.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/favourites/{id}", first.getId()).session(login(reader.getEmail())))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", "password123"))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
