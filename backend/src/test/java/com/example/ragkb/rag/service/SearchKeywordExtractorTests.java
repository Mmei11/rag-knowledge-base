package com.example.ragkb.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchKeywordExtractorTests {

    @Test
    void shouldExtractChineseBigramsAndLatinWordsWhileIgnoringQuestionWords() {
        assertThat(SearchKeywordExtractor.extract("食品安全有哪些要求 Spring Boot"))
                .contains("食品", "安全", "要求", "spring", "boot")
                .doesNotContain("哪些");
    }
}
