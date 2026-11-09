package com.example.ragkb.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChineseTextSegmenterTests {

    private final ChineseTextSegmenter segmenter = new ChineseTextSegmenter();

    @Test
    void shouldBuildSpaceSeparatedSearchText() {
        String searchText = segmenter.buildSearchText("食品安全有哪些要求 Spring Boot");

        assertThat(searchText).contains("食品", "安全", "要求", "spring", "boot");
        assertThat(searchText).doesNotContain("哪些");
        assertThat(searchText.split(" ")).hasSizeGreaterThan(1);
    }

    @Test
    void shouldSegmentQueryWithStopwordsRemoved() {
        List<String> tokens = segmenter.segmentForQuery("食品安全有哪些要求 Spring Boot");

        assertThat(tokens).contains("食品", "安全", "要求", "spring", "boot");
        assertThat(tokens).doesNotContain("哪些");
    }

    @Test
    void shouldKeepMixedChineseAndLatinTokens() {
        List<String> tokens = segmenter.segmentForQuery("HACCP，食品安全！");

        assertThat(tokens).contains("haccp", "食品", "安全");
        assertThat(tokens).noneMatch(token -> token.contains("，") || token.contains("！"));
    }

    @Test
    void shouldReturnEmptyForBlankInput() {
        assertThat(segmenter.buildSearchText(" ")).isEmpty();
        assertThat(segmenter.segmentForQuery(null)).isEmpty();
    }

    @Test
    void shouldCapQueryTokens() {
        String query = "食品安全 生产经营 管理 要求 规定 标准 检验 监督 处罚 责任 义务 权利 许可 备案 召回 追溯 记录 凭证 保存 期限";

        assertThat(segmenter.segmentForQuery(query)).hasSizeLessThanOrEqualTo(16);
    }

    @Test
    void shouldFallbackToSingleHanCharacters() {
        assertThat(segmenter.segmentForQuery("法")).containsExactly("法");
    }

    @Test
    void shouldFallbackToOriginalTextWhenNothingIsTokenizable() {
        assertThat(segmenter.buildSearchText("i")).isEqualTo("i");
    }
}
