package com.example.ragkb.rag.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchKeywordExtractor {

    private static final Pattern CHINESE_PHRASE = Pattern.compile("[\\p{IsHan}]{2,}");
    private static final Pattern LATIN_WORD = Pattern.compile("[a-zA-Z0-9_-]{2,}");
    private static final Set<String> IGNORED_TERMS = Set.of(
            "什么", "怎么", "如何", "哪些", "这个", "那个", "一下", "关于", "是否");
    private static final int MAX_KEYWORDS = 12;

    private SearchKeywordExtractor() {
    }

    static List<String> extract(String question) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addChineseTerms(question, terms);
        addLatinTerms(question, terms);

        return terms.stream()
                .filter(term -> !IGNORED_TERMS.contains(term))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private static void addChineseTerms(String question, Set<String> terms) {
        Matcher matcher = CHINESE_PHRASE.matcher(question);
        while (matcher.find()) {
            String phrase = matcher.group();
            terms.add(phrase);
            for (int index = 0; index < phrase.length() - 1; index++) {
                terms.add(phrase.substring(index, index + 2));
            }
        }
    }

    private static void addLatinTerms(String question, Set<String> terms) {
        Matcher matcher = LATIN_WORD.matcher(question);
        while (matcher.find()) {
            terms.add(matcher.group().toLowerCase(Locale.ROOT));
        }
    }
}
