package com.example.board;

import com.fasterxml.jackson.databind.*;
import org.hibernate.SessionFactory;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class BoardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManagerFactory emf;
    @BeforeEach void clean() { comments.deleteAll(); posts.deleteAll(); members.deleteAll(); }
    private String signup(String email) { return "{\"email\":\""+email+"\",\"password\":\"password123\",\"nickname\":\"Writer\"}"; }
    private MockHttpSession login(String email) throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(signup(email))).andExpect(status().isCreated());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\""+email+"\",\"password\":\"password123\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private long createPost(MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(post("/api/posts").session(session).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Spring title\",\"content\":\"Body text\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    private long createComment(long id, MockHttpSession session) throws Exception {
        return json.readTree(mvc.perform(post("/api/posts/"+id+"/comments").session(session).with(csrf()).contentType("application/json")
                .content("{\"content\":\"First comment\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void registrationValidationAndHashing() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json")
                .content(signup("bad-email"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json")
                .content(signup("a@example.com").replace("password123","short"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json")
                .content(signup("a@example.com").replace("password123","가".repeat(30)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(signup("a@example.com")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType("application/json").content(signup("A@example.com")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        var stored = members.findByEmail("a@example.com").orElseThrow();
        assertThat(stored.passwordHash).isNotEqualTo("password123");
        assertThat(encoder.matches("password123",stored.passwordHash)).isTrue();
    }
    @Test void realCsrfSessionLoginRotationAndLogout() throws Exception {
        var tokenResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession)tokenResult.getRequest().getSession(false);
        String token = json.readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(signup("a@example.com"))).andExpect(status().isCreated());
        String oldId = session.getId();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",token).contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"password123\"}")).andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("a@example.com"));
        mvc.perform(post("/api/posts").session(session).header("X-CSRF-TOKEN",token).contentType("application/json")
                .content("{\"title\":\"Old token\",\"content\":\"Body\"}")).andExpect(status().isForbidden());
        String newToken = json.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/posts").session(session).header("X-CSRF-TOKEN",newToken).contentType("application/json")
                .content("{\"title\":\"New token\",\"content\":\"Body\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",newToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void anonymousReadsAndWritesReturn401() throws Exception {
        var owner = login("a@example.com"); long postId = createPost(owner); long commentId = createComment(postId,owner);
        mvc.perform(get("/api/posts")).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+postId)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/"+postId+"/comments")).andExpect(status().isOk());
        mvc.perform(post("/api/posts").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(put("/api/posts/"+postId).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/posts/"+postId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/posts/"+postId+"/comments").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/comments/"+commentId).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/comments/"+commentId)).andExpect(status().isUnauthorized());
    }
    @Test void othersCannotUpdateOrDeletePostsOrComments() throws Exception {
        var owner = login("a@example.com"); long postId = createPost(owner); long commentId = createComment(postId,owner);
        var other = login("b@example.com");
        mvc.perform(put("/api/posts/"+postId).session(other).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Stolen\",\"content\":\"Body\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/posts/"+postId).session(other).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/comments/"+commentId).session(other).with(csrf()).contentType("application/json")
                .content("{\"content\":\"Stolen\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/comments/"+commentId).session(other).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void ownersCanUpdateDeleteAndPostDeletionRemovesComments() throws Exception {
        var owner = login("a@example.com"); long postId = createPost(owner); long commentId = createComment(postId,owner);
        mvc.perform(put("/api/posts/"+postId).session(owner).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Updated\",\"content\":\"New body\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"));
        mvc.perform(put("/api/comments/"+commentId).session(owner).with(csrf()).contentType("application/json")
                .content("{\"content\":\"Updated comment\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Updated comment"));
        mvc.perform(delete("/api/comments/"+commentId).session(owner).with(csrf())).andExpect(status().isNoContent());
        createComment(postId,owner);
        mvc.perform(delete("/api/posts/"+postId).session(owner).with(csrf())).andExpect(status().isNoContent());
        assertThat(comments.count()).isZero(); assertThat(posts.count()).isZero();
    }
    @Test void invalidRequestsMissingResourcesAndBadLoginHaveSameErrorShape() throws Exception {
        var owner = login("a@example.com");
        mvc.perform(get("/api/posts/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/posts/999999"));
        mvc.perform(get("/api/posts/999999/comments")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/comments/999999").session(owner).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put("/api/posts/999999").session(owner).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Missing\",\"content\":\"Body\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/999999/comments").session(owner).with(csrf()).contentType("application/json")
                .content("{\"content\":\"Body\"}")).andExpect(status().isNotFound());
        for (String path : new String[]{"/api/posts?page=-1","/api/posts?size=0","/api/posts?size=101","/api/posts?page=abc","/api/posts/0","/api/posts/abc"})
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.path").exists());
        mvc.perform(post("/api/posts").session(owner).with(csrf()).contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/posts").session(owner).with(csrf()).contentType("application/json").content("{\"title\":\"  \",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
    }
    @Test void paginationSearchAndCommentCountUseAtMostTwoQueries() throws Exception {
        var owner = login("a@example.com");
        long first = createPost(owner); createComment(first,owner); createComment(first,owner);
        long latest = 0;
        for (int i=0;i<12;i++) latest = createPost(owner);
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        mvc.perform(get("/api/posts?page=0&size=10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10)).andExpect(jsonPath("$.totalElements").value(13))
                .andExpect(jsonPath("$.content[0].id").value(latest)).andExpect(jsonPath("$.content[0].authorNickname").value("Writer"));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        mvc.perform(get("/api/posts?page=1&size=10")).andExpect(jsonPath("$.content[2].commentCount").value(2));
        mvc.perform(get("/api/posts?q=Spring")).andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/posts?q=Body")).andExpect(jsonPath("$.totalElements").value(13));
        mvc.perform(get("/api/posts?q=missing")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/posts?q=%")).andExpect(jsonPath("$.totalElements").value(0));
    }
}
