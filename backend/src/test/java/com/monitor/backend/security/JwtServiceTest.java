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
    void acceptsPkcs1Pem() {
        // Clé générée via `openssl genrsa` (format PKCS1 "BEGIN RSA PRIVATE KEY"), base64(PEM)
        String b64Pem = "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlFcEFJQkFBS0NBUUVBcGJxY0twbGRzT1JUWXB3NnY4VTM4R1ZnMHR3Wk9ZNVR2dFBpdVJManZET2hnemR0ClQ3ZjZ4elBWT1BIWVg0NXZYTC9xNzhMZ2ZxZmIzSG80QVdxQ0hMcitXYWZqUU4vZVpuTnREKy9wWWNTSHFrc0MKR3lZUFE3NHpiS01sb0lGblkyczBPZXphcUFhNzRGL2xRckp0WTZTMmlXZkhaS0VKTzR5Y2tIV01GZ0Ntd0RwVgo0MWFVWk1ZOU5TNVo4cmhXZVRrT0tlVWp4SG5KVDJodEZyY3B6RUhhOU9WMWt2SndpT05SOVF2dWtyU3lBK1ZvCm5mNG5ZQStVWWJPMEhnRldZUlRhQ2t1Qld0d202T0FEck9GMFdGMDd6OWQ5TUtPYXRaNTZIM3hYamM1RFJZOXUKNTd3a3BMZkp5anF2VGhuNkd2U1hNejk0NDNqRTlTazFra1BRR3dJREFRQUJBb0lCQUZNR2FiWHRuMEkvSUljRwpQV2hWSlBFSjJMZktDOWtKdEdxbE82LzhCcHNDUXFlNTNNbFRJZzhwcUdnZXdJY2VsQ3VpK2MramtWUGlSQUx3Ck41NTkvVERuZlFUdFZxdnBlMEZZRWdzcDVZRlZNNkhjY2ZHbjMzM1FpM0toU1BoZjI2dXl5VGM3c1U3OEVLTVgKWjROQ1lBckxPYWNjbG9lVEpVenY1SDk2WS9PaEVTZjhpZzRBNEtDSHIzSE1zUjYwYytuT0p6QmcyVXNCbGJ0WQpNL1loMGFVcnczcjA0bTJHUkF1Q0p0dkVjZWZxMVBzOEZBOFB3b2xuUVlaT25MMEIrdy9RUUVKOGhCdEU5aldkCmNRS2szbktjb3gyOUtrZWhKUzNzWEJNS0xDTEw5SmlqejlBdSt5STZFZXRaVHMvR1R0TjlXQWRGZEFQT05iU1IKakJqVXhnRUNnWUVBMnI4Slo0WWVuUlg5K0FJZXVUNVo1V0dMcFRodkgrR3MvQmdmMDVzZU8vYTFadmNoS3VZdwpTbC9KUm5UMnVUQ2FyUUlFWjhhaVR2N2I5bjJVMitWbDJDT1p5R3JyMHRJTE43N1Bxb2VzbW8vV2FvMTdDaENPCnpkYUNWTjkyYkxVcW13d0pObzJoZEZCRnQ3SUlCaVM0cVhGRWdxNWhmZkgvWnRmOUliZ09ZMVVDZ1lFQXdmUWMKRkxUMk91b3A5R2g4cEZ1L20wWVNGSjZQWTlzYTVMR3ZoMmYzTHlibzlIQWFaTnJadHowRHNmRUdJWk5IOUQ0Zwp3bUJQdXoxeDZPTis0aHhzSmNZZ0h2TThNeGIzdU5BV0NRMFZKM0s3aTNDRXp1aVpzTThCSHNSM005b3lzZXQ4CmJGZ2VGLzR5eVhHdmRuQ1RlbUU3OTRvZ1l5cllPNmpYdkxFSVJhOENnWUVBbW9qWmdQeGNSa2VTR3hLTVYvdjMKa0RoQWFtR2JCOS9yMEZET3N6Ump3S01DOXBIaUthZGpPRGZCUHZoRWtRSUlVKzhYNDZTTlo5ZGUyQjFSKzZOdApOcXUzNmNHM2hrR0RVVWVJK0FWUGFBWHp5V1RZSm4wYUZjclVXbGRnRjE1SmtpMkhtTFduK1Q3NGNkVGhIRTMyCjh5V2s2OWw3NEhrRklNZTMrWWxKQ04wQ2dZQkVjTFZ3QVNOYVNzTWZRYmVwNzg0L0pveSt4ZXpuV2F1MXc1V2kKL0diWE5xM0JnOFZzaldXQ0tYK2lUT0RTL0R2SnpocVZQSk1tQ1ZxbXlaQStqYlVENTFQeVYvYUlPTFNWZktncAo3WG5uZjY3NlYyd2w3eUk4cCs5SFFpQ21KWHI2R1pUQVRwL2Y2NDFVVkUrTVJkRmt2K2IvUkxpRkJkdGp3Z0xPCnpOR1VkUUtCZ1FEV2VBTnZ4RXFFRlhNeXpzNEp5TDB4ZUhiR21OQS9SRDhBQXZGeEZPSjZ0RHF4NVE3R3ptcGUKcHFmQUhpcXMvUUh1TFJ5VjgrMm9xblgwdm40N0w3NXk5eG95VzYxSnlFaElYUitIWlpaRjQ2dFRTQm81NTQxawpDRTJkUXpMMnNSRjFCNnUxMmFpQWQ5Z3lYU1dJd2Q1b3EvWlhicEt6UklTSjFxaUJFOHpwK2c9PQotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQo=";
        JwtService svc = new JwtService(b64Pem);
        String token = svc.issueToken(sampleUser());
        assertEquals("samy", svc.verify(token).username());
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
