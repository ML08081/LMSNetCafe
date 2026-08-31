package com.lms.netcafe.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private final byte[] secret;
    private final Duration ttl = Duration.ofHours(8);

    public AuthTokenService(@Value("${lms.jwt-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String createToken(String username) {
        long expiresAt = Instant.now().plus(ttl).toEpochMilli();
        String payload = encode(username + "|" + expiresAt);
        return payload + "." + sign(payload);
    }

    public Optional<String> parseUsername(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            return Optional.empty();
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split("\\|", 2);
        if (values.length != 2) {
            return Optional.empty();
        }

        long expiresAt = Long.parseLong(values[1]);
        if (Instant.now().toEpochMilli() > expiresAt) {
            return Optional.empty();
        }
        return Optional.of(values[0]);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return encode(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign auth token", ex);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
