package com.monitor.backend.security;

import com.monitor.backend.entity.Role;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.RoleRepository;
import com.monitor.backend.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Just-in-time user provisioning (D-24).
 * The Co-Note profile is created on the first login and updated on later logins from the claims of a
 * token that has ALREADY been validated (signature, issuer, audience, expiry) by the JwtDecoder.
 * The link to the institutional identity is the Keycloak "sub" claim, never the username or email.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    /** LDAP group (synchronised into Keycloak) whose members are Co-Note administrators. */
    static final String ADMIN_GROUP = "administrators";

    private final UserRepository users;
    private final RoleRepository roles;

    public UserProvisioningService(UserRepository users, RoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Transactional
    public AuthUser provision(Jwt jwt) {
        String sub = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        if (isBlank(sub) || isBlank(username)) {
            throw invalid("token has no subject or no preferred_username");
        }
        String displayName = displayName(jwt, username);
        String email = jwt.getClaimAsString("email");
        Set<String> wantedRoles = rolesFor(jwt.getClaimAsStringList("groups"));

        User user = users.findByExternalSubject(sub).orElse(null);
        if (user == null) {
            // SECURITY CHECK (D-24): never link silently to an existing local account.
            // A local account with the same username could belong to someone else.
            if (users.findByUsername(username).isPresent()) {
                log.warn("SSO login refused: username '{}' already belongs to a local account (sub={})", username, sub);
                throw invalid("username already used by a local account");
            }
            user = new User();
            user.setExternalSubject(sub);
            log.info("Creating Co-Note profile for '{}' (sub={})", username, sub);
        }

        // Update the profile from the token, but only write to the database when something changed.
        boolean changed = user.getId() == null;
        if (!Objects.equals(user.getUsername(), username)) { user.setUsername(username); changed = true; }
        if (!Objects.equals(user.getDisplayName(), displayName)) { user.setDisplayName(displayName); changed = true; }
        if (!Objects.equals(user.getEmail(), email)) { user.setEmail(email); changed = true; }

        Set<String> currentRoles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        if (!currentRoles.equals(wantedRoles)) {
            user.setRoles(wantedRoles.stream().map(this::role).collect(Collectors.toCollection(HashSet::new)));
            changed = true;
        }
        if (changed) {
            user = users.save(user);
        }
        return new AuthUser(user.getId(), user.getUsername(), wantedRoles.stream().sorted().toList());
    }

    /** Roles come from directory groups: every user is USER; members of "administrators" are also ADMIN. */
    static Set<String> rolesFor(List<String> groups) {
        Set<String> result = new HashSet<>();
        result.add("USER");
        if (groups != null) {
            for (String g : groups) {
                String name = g.startsWith("/") ? g.substring(1) : g;   // tolerate full group paths
                if (ADMIN_GROUP.equals(name)) {
                    result.add("ADMIN");
                }
            }
        }
        return result;
    }

    private static String displayName(Jwt jwt, String username) {
        String name = jwt.getClaimAsString("name");
        if (!isBlank(name)) {
            return name;
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String joined = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        return joined.isEmpty() ? username : joined;
    }

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            return roles.save(r);
        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static OAuth2AuthenticationException invalid(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", message, null), message);
    }
}
