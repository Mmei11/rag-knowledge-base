package com.example.ragkb.document.chunk;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_OVERLAP = 150;

    private final int chunkSize;
    private final int overlap;

    public TextChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    TextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be between 0 and chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<TextChunk> chunk(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int targetEnd = Math.min(start + chunkSize, normalized.length());
            int end = chooseBreakPoint(normalized, start, targetEnd);
            String content = normalized.substring(start, end).trim();
            if (!content.isBlank()) {
                chunks.add(new TextChunk(chunks.size(), content, estimateTokenCount(content)));
            }
            if (end >= normalized.length()) {
                break;
            }

            int nextStart = Math.max(0, end - overlap);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = skipLeadingWhitespace(normalized, nextStart);
        }
        return List.copyOf(chunks);
    }

    private int chooseBreakPoint(String text, int start, int targetEnd) {
        if (targetEnd >= text.length()) {
            return text.length();
        }

        int minEnd = Math.min(targetEnd, start + chunkSize / 2);
        int paragraphBreak = text.lastIndexOf("\n\n", targetEnd);
        if (paragraphBreak >= minEnd) {
            return paragraphBreak + 2;
        }

        for (int i = targetEnd - 1; i >= minEnd; i--) {
            char current = text.charAt(i);
            if (current == '\n' || isSentenceBoundary(current)) {
                return i + 1;
            }
        }
        return targetEnd;
    }

    private boolean isSentenceBoundary(char value) {
        return value == '。'
                || value == '！'
                || value == '？'
                || value == '；'
                || value == '.'
                || value == '!'
                || value == '?'
                || value == ';';
    }

    private int skipLeadingWhitespace(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private int estimateTokenCount(String content) {
        int count = 0;
        boolean inAsciiToken = false;
        for (int i = 0; i < content.length(); i++) {
            char value = content.charAt(i);
            if (Character.isWhitespace(value)) {
                inAsciiToken = false;
            } else if (isCjk(value)) {
                count++;
                inAsciiToken = false;
            } else if (Character.isLetterOrDigit(value)) {
                if (!inAsciiToken) {
                    count++;
                    inAsciiToken = true;
                }
            } else {
                inAsciiToken = false;
            }
        }
        return Math.max(count, 1);
    }

    private boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
