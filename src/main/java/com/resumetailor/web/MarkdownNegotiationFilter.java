package com.resumetailor.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Content negotiation for the public content pages (see {@link MarkdownPages}):
 * {@code Accept: text/markdown} gets the Markdown version, everything else the HTML
 * page as before. Both carry {@code Vary: Accept} so caches keep them apart.
 */
@Component
@RequiredArgsConstructor
public class MarkdownNegotiationFilter extends OncePerRequestFilter {

    private final MarkdownPages markdownPages;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !("GET".equals(method) || "HEAD".equals(method))
                || !markdownPages.hasPage(RequestPaths.pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);

        if (AcceptPreference.of(request) != AcceptPreference.Format.MARKDOWN) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = markdownPages.forPath(RequestPaths.pathOf(request)).orElseThrow()
                .getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(AcceptPreference.TEXT_MARKDOWN.toString());
        response.setContentLength(body.length);
        if (!"HEAD".equals(request.getMethod())) {
            response.getOutputStream().write(body);
        }
    }
}
