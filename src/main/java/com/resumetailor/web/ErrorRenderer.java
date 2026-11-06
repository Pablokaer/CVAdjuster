package com.resumetailor.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders every error response, from the /error page and from the exception handler,
 * in the format the client can read:
 * <ul>
 *   <li>API paths (/api/**) always get JSON problem details (RFC 9457) with a
 *       machine-readable {@code code} and a {@code hint} on how to fix the request.</li>
 *   <li>Other paths get Markdown for {@code Accept: text/markdown}, JSON when JSON is
 *       preferred, and the HTML error page otherwise.</li>
 * </ul>
 * Error bodies never contain exception messages, only the texts defined here or a
 * detail the caller passes as safe to show.
 */
@Component
public class ErrorRenderer {

    public static final String SITE = "https://cvfitai.com";
    public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    public Object render(HttpServletRequest request, HttpServletResponse response,
                         int status, String code, String detail) {
        return render(request, response, status, code, detail, RequestPaths.pathOf(request));
    }

    public Object render(HttpServletRequest request, HttpServletResponse response,
                         int status, String code, String detail, String path) {
        ErrorInfo info = ErrorInfo.of(status, code, detail, path);
        boolean api = RequestPaths.isApi(path);
        AcceptPreference.Format format = api ? AcceptPreference.Format.JSON : AcceptPreference.of(request);
        if (!api) {
            // The same URL answers in HTML, Markdown or JSON depending on Accept.
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        }

        return switch (format) {
            case JSON -> ResponseEntity.status(status).contentType(PROBLEM_JSON).body(problem(info, path));
            case MARKDOWN -> ResponseEntity.status(status).contentType(AcceptPreference.TEXT_MARKDOWN)
                    .body(markdown(info));
            case HTML -> html(info);
        };
    }

    static Map<String, Object> problem(ErrorInfo info, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", info.title());
        body.put("status", info.status());
        body.put("detail", info.detail());
        body.put("instance", RequestPaths.safe(path));
        body.put("code", info.code());
        body.put("hint", info.hint());
        body.put("docs", SITE + "/openapi.json");
        return body;
    }

    static String markdown(ErrorInfo info) {
        return """
                # %s (%d)

                %s

                %s

                ## Where to go next

                - [Home](%s/)
                - [About CV Fit AI](%s/about)
                - [Guide for AI agents (llms.txt)](%s/llms.txt)
                - [Sitemap](%s/sitemap.xml)
                - [API reference (OpenAPI)](%s/openapi.json)
                """.formatted(info.heading(), info.status(), info.detail(), info.hint(),
                SITE, SITE, SITE, SITE, SITE);
    }

    private static ModelAndView html(ErrorInfo info) {
        String view = info.status() == 429 ? "error/429" : "error";
        ModelAndView mav = new ModelAndView(view, HttpStatus.valueOf(info.status()));
        mav.addObject("status", info.status());
        mav.addObject("heading", info.heading());
        mav.addObject("detail", info.detail());
        return mav;
    }

    /** The texts of one error response. */
    record ErrorInfo(int status, String code, String title, String heading, String detail, String hint) {

        static ErrorInfo of(int status, String code, String detail, String path) {
            HttpStatus known = HttpStatus.resolve(status);
            String title = known != null ? known.getReasonPhrase() : "Error";
            String safePath = RequestPaths.safe(path);

            String defaultCode;
            String heading;
            String defaultDetail;
            String hint;
            switch (status) {
                case 400 -> {
                    defaultCode = "invalid_request";
                    heading = "Invalid request";
                    defaultDetail = "The request is missing a field or has an invalid value.";
                    hint = "Check the request against the API reference at " + SITE + "/openapi.json.";
                }
                case 401 -> {
                    defaultCode = "authentication_required";
                    heading = "Sign in required";
                    defaultDetail = "You need to sign in to use this.";
                    hint = "Sign in at " + SITE + "/login. The API uses the session cookie of a signed-in "
                            + "session, as described in " + SITE + "/openapi.json.";
                }
                case 403 -> {
                    defaultCode = "forbidden";
                    heading = "Request refused";
                    defaultDetail = "This request was refused. If you were filling in a form, reload the page and try again.";
                    hint = "Requests that change data need the session cookie and the CSRF token from the page's "
                            + "_csrf meta tag, sent in the X-CSRF-TOKEN header.";
                }
                case 404 -> {
                    defaultCode = "not_found";
                    heading = "Page not found";
                    defaultDetail = "There is no page or resource at " + safePath + " on CV Fit AI.";
                    hint = "Check the address. Public pages are listed in " + SITE + "/sitemap.xml, "
                            + SITE + "/llms.txt describes the site for AI agents and "
                            + SITE + "/openapi.json describes the API.";
                }
                case 405 -> {
                    defaultCode = "method_not_allowed";
                    heading = "Method not allowed";
                    defaultDetail = "This address does not accept this HTTP method.";
                    hint = "Use one of the methods in the Allow header, or see " + SITE + "/openapi.json.";
                }
                case 413 -> {
                    defaultCode = "payload_too_large";
                    heading = "File too large";
                    defaultDetail = "The upload is too large. Files can be up to 10 MB.";
                    hint = "Upload a smaller PDF or DOCX file.";
                }
                case 415 -> {
                    defaultCode = "unsupported_media_type";
                    heading = "Unsupported content type";
                    defaultDetail = "The request body has a content type this address does not accept.";
                    hint = "Send the content type listed for this operation in " + SITE + "/openapi.json.";
                }
                case 429 -> {
                    defaultCode = "rate_limited";
                    heading = "Slow down a little";
                    defaultDetail = "Too many requests in a short time.";
                    hint = "Wait the number of seconds in the Retry-After header, then try again.";
                }
                case 502, 503, 504 -> {
                    defaultCode = "upstream_unavailable";
                    heading = "Service temporarily unavailable";
                    defaultDetail = "A service we depend on did not respond. Please try again in a moment.";
                    hint = "Retry later. Nothing was charged for a request that failed.";
                }
                default -> {
                    if (status >= 500) {
                        defaultCode = "internal_error";
                        heading = "Something went wrong";
                        defaultDetail = "Something went wrong on our side. Please try again in a moment.";
                        hint = "Retry later. If it keeps happening, contact support through " + SITE + "/privacy.";
                    } else {
                        defaultCode = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
                        heading = title;
                        defaultDetail = "The request could not be completed.";
                        hint = "See " + SITE + "/openapi.json for the API and " + SITE + "/llms.txt for the site.";
                    }
                }
            }

            return new ErrorInfo(status,
                    code != null ? code : defaultCode,
                    title,
                    heading,
                    detail != null && !detail.isBlank() ? detail : defaultDetail,
                    hint);
        }
    }
}
