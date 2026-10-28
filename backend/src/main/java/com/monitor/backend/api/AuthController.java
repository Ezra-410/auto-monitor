package com.monitor.backend.api;

import com.monitor.backend.entity.User;
import com.monitor.backend.repo.Repositories.UserRepository;
import com.monitor.backend.security.AuthUser;
import com.monitor.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }
        String token = jwtService.issueToken(user);
        List<String> roles = user.getRoles().stream().map(r -> r.getName()).sorted().toList();
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "display_name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                        "roles", roles)));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthUser current = ApiSupport.currentUser();
        User user = users.findById(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "display_name", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                "roles", current.roles());
    }
}
