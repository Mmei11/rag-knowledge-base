package com.example.ragkb.common.text;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.SegToken;
import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ChineseTextSegmenter {

    private static final Logger log = LoggerFactory.getLogger(ChineseTextSegmenter.class);

    private static final int MAX_SEARCH_TOKENS = 512;
    private static final int MAX_SEARCH_TEXT_CHARS = 512;
    private static final int MAX_QUERY_TOKENS = 16;
    private static final Pattern HAN_TOKEN = Pattern.compile("\\p{IsHan}{2,}");
    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern SINGLE_HAN = Pattern.compile("\\p{IsHan}");
    private static final Set<String> STOPWORDS = Set.of(
            "什么", "怎么", "如何", "哪些", "这个", "那个", "一下", "关于",
            "是否", "请问", "能否", "的话", "以及");

    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    @PostConstruct
    void warmUp() {
        try {
            jiebaSegmenter.process("预热中文分词", SegMode.INDEX);
        } catch (RuntimeException exception) {
            log.warn("Failed to warm up jieba segmenter", exception);
        }
    }

    public String buildSearchText(String content) {
        List<String> tokens = tokenize(content);
        if (tokens.isEmpty()) {
            String fallback = Normalizer.normalize(content == null ? "" : content, Normalizer.Form.NFKC).trim();
            return fallback.substring(0, Math.min(fallback.length(), MAX_SEARCH_TEXT_CHARS));
        }
        return String.join(" ", tokens.subList(0, Math.min(tokens.size(), MAX_SEARCH_TOKENS)));
    }

    public List<String> segmentForQuery(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return singleHanFallback(text);
        }
        return new LinkedHashSet<>(tokens).stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(MAX_QUERY_TOKENS)
                .toList();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).trim();
        List<String> tokens = new ArrayList<>();
        for (SegToken token : jiebaSegmenter.process(normalized, SegMode.INDEX)) {
            String word = normalizeToken(token.word);
            if (word != null) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private String normalizeToken(String raw) {
        if (raw == null) {
            return null;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (value.isEmpty() || STOPWORDS.contains(value)) {
            return null;
        }
        if (HAN_TOKEN.matcher(value).matches()) {
            return value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return LATIN_TOKEN.matcher(lower).matches() ? lower : null;
    }

    private List<String> singleHanFallback(String text) {
        if (text == null) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        LinkedHashSet<String> chars = new LinkedHashSet<>();
        for (int index = 0; index < normalized.length() && chars.size() < MAX_QUERY_TOKENS; index++) {
            String value = normalized.substring(index, index + 1);
            if (SINGLE_HAN.matcher(value).matches() && !STOPWORDS.contains(value)) {
                chars.add(value);
            }
        }
        return List.copyOf(chars);
    }
}
