package com.example.ragkb.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ragkb.knowledgebase.dto.KnowledgeBaseResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisConfigTests {

    @Test
    void shouldSerializeCacheValueWithOffsetDateTime() {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse(
                10L,
                1L,
                "Java Notes",
                "Spring Boot",
                2,
                OffsetDateTime.parse("2026-09-05T12:00:00+08:00"),
                OffsetDateTime.parse("2026-09-05T12:30:00+08:00"));

        byte[] bytes = RedisConfig.jsonRedisSerializer().serialize(response);
        Object restored = RedisConfig.jsonRedisSerializer().deserialize(bytes);

        assertThat(restored).isInstanceOf(KnowledgeBaseResponse.class);
        KnowledgeBaseResponse restoredResponse = (KnowledgeBaseResponse) restored;
        assertThat(restoredResponse.id()).isEqualTo(response.id());
        assertThat(restoredResponse.name()).isEqualTo(response.name());
        assertThat(restoredResponse.createdAt().toInstant()).isEqualTo(response.createdAt().toInstant());
        assertThat(restoredResponse.updatedAt().toInstant()).isEqualTo(response.updatedAt().toInstant());
    }

    @Test
    void shouldSerializeCacheListValue() {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse(
                10L,
                1L,
                "Java Notes",
                "Spring Boot",
                2,
                OffsetDateTime.parse("2026-09-05T12:00:00+08:00"),
                OffsetDateTime.parse("2026-09-05T12:30:00+08:00"));

        byte[] bytes = RedisConfig.jsonRedisSerializer().serialize(List.of(response));
        Object restored = RedisConfig.jsonRedisSerializer().deserialize(bytes);

        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).singleElement().isInstanceOf(KnowledgeBaseResponse.class);
    }
}
