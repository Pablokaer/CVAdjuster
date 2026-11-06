package com.resumetailor.web;

import jakarta.servlet.http.HttpServletRequest;

/** Small helpers for request paths used by the error and negotiation code. */
public final class RequestPaths {

    private static final int MAX_SHOWN_LENGTH = 200;

    private RequestPaths() {
    }

    /** The request path without the context path or query string. */
    public static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (uri == null) return "/";
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    public static boolean isApi(String path) {
        return path != null && (path.equals("/api") || path.startsWith("/api/"));
    }

    public static boolean isApi(HttpServletRequest request) {
        return isApi(pathOf(request));
    }

    /**
     * A path that is safe to echo back inside Markdown, JSON or HTML text: printable
     * characters only, no backticks or angle brackets, and at most 200 characters.
     */
    public static String safe(String path) {
        if (path == null || path.isEmpty()) return "/";
        String cleaned = path.replaceAll("[^\\x21-\\x7E]", "").replaceAll("[`<>\\\\]", "");
        return cleaned.length() > MAX_SHOWN_LENGTH ? cleaned.substring(0, MAX_SHOWN_LENGTH) + "..." : cleaned;
    }
}
