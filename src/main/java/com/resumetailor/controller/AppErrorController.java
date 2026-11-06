package com.resumetailor.controller;

import com.resumetailor.web.ErrorRenderer;
import com.resumetailor.web.RequestPaths;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The container's error page: requests that end in response.sendError (unknown paths,
 * rate limits, CSRF failures, missing sign-in on the API) are rendered here, as JSON
 * for the API, Markdown for agents that ask for it, and HTML otherwise.
 * Replaces Spring Boot's BasicErrorController.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("${server.error.path:/error}")
public class AppErrorController implements ErrorController {

    private final ErrorRenderer errorRenderer;

    @RequestMapping
    public Object error(HttpServletRequest request, HttpServletResponse response) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (!(statusAttribute instanceof Integer status)) {
            // /error opened directly: there is no error to show, so it is not a page.
            return errorRenderer.render(request, response, 404, null, null, RequestPaths.pathOf(request));
        }

        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri instanceof String s ? stripContext(request, s) : RequestPaths.pathOf(request);

        String code = null;
        String detail = null;
        if (status == 403 && request.getAttribute(WebAttributes.ACCESS_DENIED_403) instanceof CsrfException) {
            code = "csrf_token_invalid";
            detail = "The CSRF token is missing or invalid. If you were filling in a form, reload the page and try again.";
        }
        return errorRenderer.render(request, response, status, code, detail, path);
    }

    private static String stripContext(HttpServletRequest request, String uri) {
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
