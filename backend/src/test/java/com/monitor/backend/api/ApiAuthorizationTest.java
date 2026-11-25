package com.monitor.backend.api;

import com.monitor.backend.entity.Note;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.NoteRepository;
import com.monitor.backend.repo.UserRepository;
import com.monitor.backend.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 4 scenarios, checked against the REAL SecurityConfig rules:
 * the right scope gives 200/201, a missing scope gives 403, no token gives 401,
 * and a service account is kept out of user endpoints.
 */
@WebMvcTest(controllers = {NoteController.class, SyncController.class, AuthController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.oidc.issuer=http://issuer.test/realms/conote")
class ApiAuthorizationTest {

    @Autowired MockMvc mvc;
    @MockitoBean NoteRepository notes;
    @MockitoBean UserRepository users;
    @MockitoBean KeycloakJwtConverter converter;   // replaced: tests set the authentication directly

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setId(7L);
        u.setUsername("teacher01");
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(users.getReferenceById(7L)).thenReturn(u);
        when(notes.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notes.findByOwner_IdOrderByUpdatedAtDesc(7L)).thenReturn(List.of());
        when(notes.findTop500ByUpdatedAtAfterOrderByUpdatedAtAsc(any(Instant.class))).thenReturn(List.of());
    }

    private static Jwt jwt(String scope, boolean service) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("scope", scope);
        if (service) b.claim("client_id", "conote-sync");
        return b.build();
    }

    /** A person (teacher01) whose token carries the given scopes. */
    private static AbstractAuthenticationToken user(String... scopes) {
        List<GrantedAuthority> a = new ArrayList<>();
        a.add(new SimpleGrantedAuthority("ROLE_USER"));
        for (String s : scopes) a.add(new SimpleGrantedAuthority("SCOPE_" + s));
        return new CoNoteAuthentication(new AuthUser(7L, "teacher01", List.of("USER")), jwt(String.join(" ", scopes), false), a);
    }

    /** The conote-sync machine with the given scopes. */
    private static AbstractAuthenticationToken service(String... scopes) {
        List<GrantedAuthority> a = new ArrayList<>();
        a.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
        for (String s : scopes) a.add(new SimpleGrantedAuthority("SCOPE_" + s));
        return new ServiceAccountAuthentication(jwt(String.join(" ", scopes), true), a);
    }

    private static final String NOTE = "{\"title\":\"Lab 2\",\"body\":\"OIDC notes\"}";

    @Test void readNotesWithNotesRead200() throws Exception {
        mvc.perform(get("/api/v1/notes").with(authentication(user("notes:read")))).andExpect(status().isOk());
    }

    @Test void writeNoteWithNotesWrite201() throws Exception {
        mvc.perform(post("/api/v1/notes").contentType(MediaType.APPLICATION_JSON).content(NOTE)
                .with(authentication(user("notes:write")))).andExpect(status().isCreated());
    }

    @Test void writeNoteWithOnlyNotesRead403() throws Exception {
        mvc.perform(post("/api/v1/notes").contentType(MediaType.APPLICATION_JSON).content(NOTE)
                .with(authentication(user("notes:read")))).andExpect(status().isForbidden());
    }

    @Test void readNotesWithOnlyNotesWrite403() throws Exception {
        mvc.perform(get("/api/v1/notes").with(authentication(user("notes:write")))).andExpect(status().isForbidden());
    }

    @Test void deleteNoteWithOnlyNotesRead403() throws Exception {
        mvc.perform(delete("/api/v1/notes/1").with(authentication(user("notes:read")))).andExpect(status().isForbidden());
    }

    @Test void meNeedsProfileRead() throws Exception {
        mvc.perform(get("/api/v1/auth/me").with(authentication(user("profile:read")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").with(authentication(user("notes:read")))).andExpect(status().isForbidden());
    }

    @Test void otherUsersNoteIsNotFound404() throws Exception {
        when(notes.findByIdAndOwner_Id(99L, 7L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/notes/99").with(authentication(user("notes:read")))).andExpect(status().isNotFound());
    }

    @Test void noToken401() throws Exception {
        mvc.perform(get("/api/v1/notes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sync/notes")).andExpect(status().isUnauthorized());
    }

    @Test void serviceAccountReadsSyncEndpoint200() throws Exception {
        mvc.perform(get("/api/v1/sync/notes").with(authentication(service("notes:read")))).andExpect(status().isOk());
    }

    @Test void serviceAccountRefusedOnUserEndpoints403() throws Exception {
        mvc.perform(get("/api/v1/notes").with(authentication(service("notes:read")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").with(authentication(service("notes:read", "profile:read")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects").with(authentication(service("notes:read")))).andExpect(status().isForbidden());
    }

    @Test void userRefusedOnSyncEndpoint403() throws Exception {
        mvc.perform(get("/api/v1/sync/notes").with(authentication(user("notes:read")))).andExpect(status().isForbidden());
    }

    @Test void badSinceParameterRejected400() throws Exception {
        mvc.perform(get("/api/v1/sync/notes").param("since", "yesterday")
                .with(authentication(service("notes:read")))).andExpect(status().isBadRequest());
    }

    @Test void serviceAccountWithoutScopeRefused403() throws Exception {
        mvc.perform(get("/api/v1/sync/notes").with(authentication(service()))).andExpect(status().isForbidden());
    }

    @Test void emptyTitleRejected400() throws Exception {
        mvc.perform(post("/api/v1/notes").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}")
                .with(authentication(user("notes:write")))).andExpect(status().isBadRequest());
    }
}
