package com.monitor.backend.security;

import com.monitor.backend.entity.Role;
import com.monitor.backend.entity.User;
import com.monitor.backend.repo.RoleRepository;
import com.monitor.backend.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    UserRepository users;
    @Mock
    RoleRepository roles;

    UserProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new UserProvisioningService(users, roles);
        lenient().when(roles.findByName(anyString())).thenAnswer(inv -> Optional.of(role(inv.getArgument(0))));
        lenient().when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(42L);
            }
            return u;
        });
    }

    private static Role role(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    private static Jwt token(String... groups) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("kc-sub-1")
                .claim("preferred_username", "student01")
                .claim("given_name", "Amina")
                .claim("family_name", "Bello")
                .claim("email", "student01@conote.edu")
                .claim("groups", List.of(groups))
                .build();
    }

    @Test
    void createsProfileOnFirstLogin() {
        when(users.findByExternalSubject("kc-sub-1")).thenReturn(Optional.empty());
        when(users.findByUsername("student01")).thenReturn(Optional.empty());

        AuthUser result = service.provision(token("students"));

        assertEquals(42L, result.id());
        assertEquals(List.of("USER"), result.roles());
        verify(users).save(argThat(u -> "kc-sub-1".equals(u.getExternalSubject())
                && "Amina Bello".equals(u.getDisplayName())
                && "student01@conote.edu".equals(u.getEmail())));
    }

    @Test
    void doesNotWriteWhenNothingChanged() {
        User existing = new User();
        existing.setId(7L);
        existing.setExternalSubject("kc-sub-1");
        existing.setUsername("student01");
        existing.setDisplayName("Amina Bello");
        existing.setEmail("student01@conote.edu");
        existing.setRoles(new HashSet<>(Set.of(role("USER"))));
        when(users.findByExternalSubject("kc-sub-1")).thenReturn(Optional.of(existing));

        AuthUser result = service.provision(token("students"));

        assertEquals(7L, result.id());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void administratorsGroupGivesAdminRole() {
        when(users.findByExternalSubject("kc-sub-1")).thenReturn(Optional.empty());
        when(users.findByUsername("student01")).thenReturn(Optional.empty());

        AuthUser result = service.provision(token("administrators"));

        assertEquals(List.of("ADMIN", "USER"), result.roles());
    }

    @Test
    void refusesToLinkToAnExistingLocalAccount() {
        User local = new User();
        local.setId(1L);
        local.setUsername("student01");
        when(users.findByExternalSubject("kc-sub-1")).thenReturn(Optional.empty());
        when(users.findByUsername("student01")).thenReturn(Optional.of(local));

        assertThrows(OAuth2AuthenticationException.class, () -> service.provision(token("students")));
        verify(users, never()).save(any(User.class));
    }
}
