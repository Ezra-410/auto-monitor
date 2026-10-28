package com.monitor.backend.security;

import com.monitor.backend.entity.User;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private static final String KEY_ID = "monitor-1";
    private static final Duration TTL = Duration.ofHours(12);

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtService(@Value("${app.jwt-private-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("JWT_PRIVATE_KEY manquant (voir scripts/gen-jwt-key.sh)");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            String asText = new String(decoded);
            byte[] der;
            if (asText.contains("BEGIN")) {
                String pem = asText.replaceAll("-----BEGIN [^-]+-----", "")
                        .replaceAll("-----END [^-]+-----", "")
                        .replaceAll("\\s", "");
                der = Base64.getDecoder().decode(pem);
            } else {
                der = decoded;
            }
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.privateKey = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            this.publicKey = (RSAPublicKey) kf.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY invalide (base64 PEM/DER PKCS8 attendu)", e);
        }
    }

    public String issueToken(User user) {
        List<String> roles = user.getRoles().stream().map(r -> r.getName()).sorted().toList();
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .claim("uid", user.getId())
                    .claim("roles", roles)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plus(TTL)))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de signer le JWT", e);
        }
    }

    public AuthUser verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new SecurityException("Signature JWT invalide");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                throw new SecurityException("JWT expiré");
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            Long uid = claims.getLongClaim("uid");
            return new AuthUser(uid, claims.getSubject(), roles == null ? List.of() : roles);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("JWT invalide", e);
        }
    }

    public Map<String, Object> jwks() {
        RSAKey jwk = new RSAKey.Builder(publicKey).keyID(KEY_ID).build();
        return new JWKSet(jwk.toPublicJWK()).toJSONObject();
    }
}
