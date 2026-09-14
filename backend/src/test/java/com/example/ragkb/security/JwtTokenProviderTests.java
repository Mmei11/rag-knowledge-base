package com.example.ragkb.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragkb.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTests {

    @Test
    void shouldGenerateAndParseToken() {
        JwtTokenProvider jwtTokenProvider =
                new JwtTokenProvider(new ObjectMapper(), "test-secret", 86400L);
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);

        String token = jwtTokenProvider.generateToken(user);

        assertThat(token).contains(".");
        assertThat(jwtTokenProvider.parseToken(token))
                .hasValue(new CurrentUser(1L, "alice", user.getRole()));
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtTokenProvider jwtTokenProvider =
                new JwtTokenProvider(new ObjectMapper(), "test-secret", 86400L);
        User user = new User("alice", "encoded-password", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);

        String token = jwtTokenProvider.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 1) + "x";

        assertThat(jwtTokenProvider.parseToken(tamperedToken)).isEmpty();
    }
}
