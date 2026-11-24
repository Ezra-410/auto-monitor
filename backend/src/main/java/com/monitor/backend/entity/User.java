package com.monitor.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Legacy local accounts only. Users created through Keycloak (SSO) have no local password. */
    @JsonIgnore
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Keycloak "sub" claim: the permanent link between this row and the institutional identity (D-24). */
    @Column(name = "external_subject", unique = true, length = 64)
    private String externalSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
