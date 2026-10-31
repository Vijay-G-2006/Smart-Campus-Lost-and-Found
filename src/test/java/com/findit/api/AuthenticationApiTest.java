package com.findit.api;

import com.findit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication and authorisation of the account endpoints (PRD FR-01).
 */
class AuthenticationApiTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a visitor can register and always gets the USER role")
    void registerCreatesUserRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new.student@findit.local","displayName":"New Student","password":"Str0ngPass123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value("new.student@findit.local"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("a registration cannot request an administrator role")
    void registerIgnoresRequestedRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sneaky@findit.local","displayName":"Sneaky","password":"Str0ngPass123","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("the registration validates the payload")
    void registerValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","displayName":"x","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("the same email address cannot be registered twice")
    void registerRejectsDuplicateEmail() throws Exception {
        createUser("duplicate@findit.local", com.findit.domain.Role.USER);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"duplicate@findit.local","displayName":"Duplicate","password":"Str0ngPass123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("signing in returns the user and a session")
    void loginWorks() throws Exception {
        createUser("student@findit.local", com.findit.domain.Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .param("email", "student@findit.local")
                        .param("password", "Str0ngPass123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("student@findit.local"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    @DisplayName("a wrong password is rejected without revealing why")
    void loginFailsSafely() throws Exception {
        createUser("student2@findit.local", com.findit.domain.Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .param("email", "student2@findit.local")
                        .param("password", "TheWrongPassword1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("an unknown email address gives the same error message")
    void unknownEmailGivesSameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrfToken())
                        .param("email", "nobody@findit.local")
                        .param("password", "Str0ngPass123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("the current user endpoint requires a session")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the current user endpoint returns the signed in user")
    void meReturnsUser() throws Exception {
        var user = createUser("me@findit.local", com.findit.domain.Role.USER);

        mockMvc.perform(get("/api/auth/me").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@findit.local"));
    }

    @Test
    @DisplayName("a state changing request without a CSRF token is rejected")
    void csrfIsEnforced() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"csrf@findit.local","displayName":"Csrf","password":"Str0ngPass123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the health endpoint is public")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
