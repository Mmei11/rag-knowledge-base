package com.example.ragkb.security;

import com.example.ragkb.user.entity.User;
import com.example.ragkb.user.entity.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", user.getRole().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(expirationSeconds).getEpochSecond());

        String encodedHeader = base64UrlEncode(toJson(header));
        String encodedPayload = base64UrlEncode(toJson(payload));
        String signingInput = encodedHeader + "." + encodedPayload;

        return signingInput + "." + sign(signingInput);
    }

    public Optional<CurrentUser> parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(signingInput), parts[2])) {
                return Optional.empty();
            }

            Map<String, Object> payload = objectMapper.readValue(base64UrlDecode(parts[1]), MAP_TYPE);
            long expiresAt = numberClaim(payload, "exp");
            if (Instant.now().getEpochSecond() >= expiresAt) {
                return Optional.empty();
            }

            Long userId = numberClaim(payload, "sub");
            String username = stringClaim(payload, "username");
            UserRole role = UserRole.valueOf(stringClaim(payload, "role"));
            return Optional.of(new CurrentUser(userId, username, role));
        } catch (RuntimeException | java.io.IOException ex) {
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    private byte[] toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to serialize JWT content", ex);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private long numberClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("JWT claim is not a number: " + name);
    }

    private String stringClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("JWT claim is not a string: " + name);
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
