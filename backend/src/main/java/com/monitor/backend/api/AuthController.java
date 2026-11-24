package com.monitor.backend.api;

import com.monitor.backend.entity.User;
import com.monitor.backend.repo.UserRepository;
import com.monitor.backend.security.AuthUser;
import com.monitor.backend.security.CoNoteAuthentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Milestone 3: the password login (/login) is gone; Keycloak authenticates users.
 * /me returns the Co-Note profile created or updated from the token (D-24). The frontend's
 * OIDC callback calls it right after login, so it also records the login time.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        AuthUser current = ApiSupport.currentUser();
        User user = users.findById(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        user.setLastLoginAt(Instant.now());
        users.save(user);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("username", user.getUsername());
        body.put("display_name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        body.put("email", user.getEmail());
        body.put("roles", current.roles());
        if (authentication instanceof CoNoteAuthentication auth) {
            Jwt token = auth.getToken();
            body.put("groups", token.getClaimAsStringList("groups"));
            body.put("student_id", token.getClaimAsString("studentId"));
            body.put("department", token.getClaimAsString("department"));
        }
        return body;
    }
}
