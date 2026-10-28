package com.monitor.backend.security;

import com.monitor.backend.entity.Role;
import com.monitor.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService serviceWithFreshKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey priv = (RSAPrivateKey) gen.generateKeyPair().getPrivate();
        String b64 = Base64.getEncoder().encodeToString(priv.getEncoded());
        return new JwtService(b64);
    }

    private User sampleUser() {
        Role admin = new Role();
        admin.setId(1L);
        admin.setName("ADMIN");
        User u = new User();
        u.setId(42L);
        u.setUsername("samy");
        u.setRoles(Set.of(admin));
        return u;
    }

    @Test
    void issueAndVerifyRoundTrip() throws Exception {
        JwtService svc = serviceWithFreshKey();
        String token = svc.issueToken(sampleUser());
        AuthUser auth = svc.verify(token);
        assertEquals("samy", auth.username());
        assertEquals(42L, auth.id());
        assertTrue(auth.roles().contains("ADMIN"));
    }

    @Test
    void jwksExposesPublicKey() throws Exception {
        JwtService svc = serviceWithFreshKey();
        var jwks = svc.jwks();
        assertTrue(jwks.containsKey("keys"));
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        JwtService svc = serviceWithFreshKey();
        assertThrows(SecurityException.class, () -> svc.verify("not.a.jwt"));
    }

    @Test
    void acceptsBase64Pem() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey priv = (RSAPrivateKey) gen.generateKeyPair().getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(priv.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        JwtService svc = new JwtService(Base64.getEncoder().encodeToString(pem.getBytes()));
        assertNotNull(svc.issueToken(sampleUser()));
    }
}
