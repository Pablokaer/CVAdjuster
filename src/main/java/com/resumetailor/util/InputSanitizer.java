package com.resumetailor.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lightweight input sanitizer for user-provided job descriptions.
 *
 * This class performs non-destructive sanitization (removes code fences, HTML tags,
 * suspicious instruction-like lines and control characters) and provides a detection
 * helper to flag likely prompt-injection patterns.
 */
public final class InputSanitizer {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?```", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKTICKS = Pattern.compile("`+");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\u0000-\u001F\u007F]");

    // Lines that look like explicit instructions to an assistant
    private static final Pattern INJECTION_LINES = Pattern.compile(
        "(?im)^(.*\\b(ignore previous instructions|disregard previous instructions|do not follow|only output|only respond with|system:|assistant:|user:|execute|run command|curl |wget |ssh |rm -rf|open port|send request).*?)$"
    );

    // Additional suspicious tokens (case-insensitive)
    private static final String[] SUSPICIOUS_TOKENS = new String[] {
        "ignore previous",
        "disregard previous",
        "only output",
        "only respond",
        "system:",
        "assistant:",
        "user:",
        "execute",
        "run command",
        "curl",
        "wget",
        "ssh",
        "rm -rf",
        "open port",
        "send request"
    };

    private InputSanitizer() {}

    /**
     * Sanitizes the job description by removing code fences, HTML tags, control chars,
     * suspicious lines and excessive whitespace. Also truncates to the provided
     * maxWords (if > 0).
     */
    public static String sanitizeJobDescription(String jd, int maxWords) {
        if (jd == null) return "";

        String s = jd.replace("\r\n", "\n").replace('\r', '\n');

        // Remove code fences (```...```)
        s = CODE_FENCE.matcher(s).replaceAll(" ");

        // Remove HTML tags
        s = HTML_TAGS.matcher(s).replaceAll(" ");

        // Remove suspicious instruction-like lines
        s = INJECTION_LINES.matcher(s).replaceAll(" ");

        // Remove backticks and control characters
        s = BACKTICKS.matcher(s).replaceAll(" ");
        s = CONTROL_CHARS.matcher(s).replaceAll(" ");

        // Normalize whitespace
        s = s.replaceAll("\\s+", " ").trim();

        if (maxWords > 0) {
            String[] words = s.split("\\s+");
            if (words.length > maxWords) {
                s = String.join(" ", Arrays.copyOf(words, maxWords));
            }
        }

        return s;
    }

    /**
     * Overload with default maxWords=3000 (matches application validation).
     */
    public static String sanitizeJobDescription(String jd) {
        return sanitizeJobDescription(jd, 3000);
    }

    /**
     * Heuristic detection for potentially malicious prompt-injection content.
     * Returns true if suspicious tokens or instruction-like lines are present.
     */
    public static boolean containsPromptInjection(String jd) {
        if (jd == null || jd.isBlank()) return false;
        String lower = jd.toLowerCase(Locale.ROOT);

        // Quick token check
        for (String token : SUSPICIOUS_TOKENS) {
            if (lower.contains(token)) return true;
        }

        // Regex line-level detection
        if (INJECTION_LINES.matcher(jd).find()) return true;

        return false;
    }
}

