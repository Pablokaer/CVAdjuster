package com.resumetailor.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Set;

/**
 * Picks the response format a client asked for in its Accept header.
 *
 * Only explicitly named types count: browsers send text/html and wildcards, so they
 * keep getting HTML. Markdown is served when text/markdown is listed with a quality
 * at least as high as HTML and JSON (agents usually send only text/markdown).
 */
public final class AcceptPreference {

    public enum Format { HTML, MARKDOWN, JSON }

    public static final MediaType TEXT_MARKDOWN = MediaType.parseMediaType("text/markdown;charset=UTF-8");

    private static final Set<String> MARKDOWN_TYPES = Set.of("text/markdown", "text/x-markdown");
    private static final Set<String> HTML_TYPES = Set.of("text/html", "application/xhtml+xml");

    private AcceptPreference() {
    }

    public static Format of(HttpServletRequest request) {
        return of(request.getHeader(HttpHeaders.ACCEPT));
    }

    public static Format of(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return Format.HTML;
        }
        List<MediaType> accepted;
        try {
            accepted = MediaType.parseMediaTypes(acceptHeader);
        } catch (InvalidMediaTypeException e) {
            return Format.HTML;
        }

        double markdown = 0, html = 0, json = 0;
        for (MediaType type : accepted) {
            if (type.isWildcardType() || type.isWildcardSubtype()) continue;
            String name = type.getType() + "/" + type.getSubtype();
            double quality = type.getQualityValue();
            if (MARKDOWN_TYPES.contains(name)) markdown = Math.max(markdown, quality);
            else if (HTML_TYPES.contains(name)) html = Math.max(html, quality);
            else if (name.equals("application/json") || name.endsWith("+json")) json = Math.max(json, quality);
        }

        if (markdown > 0 && markdown >= html && markdown >= json) return Format.MARKDOWN;
        if (json > 0 && json > html) return Format.JSON;
        return Format.HTML;
    }
}
