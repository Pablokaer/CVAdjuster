package com.resumetailor.controller;

import com.resumetailor.exception.EmailAlreadyRegisteredException;
import com.resumetailor.exception.InvalidTokenException;
import com.resumetailor.payment.exception.InvalidPlanException;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.web.ErrorRenderer;
import com.resumetailor.web.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exceptions from controllers. The sign-in, registration and upload forms keep their
 * own pages; everything else is rendered by {@link ErrorRenderer} with the right HTTP
 * status: JSON problem details on /api/**, Markdown or HTML elsewhere.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorRenderer errorRenderer;

    @ExceptionHandler(InvalidTokenException.class)
    public String handleInvalidToken(InvalidTokenException e) {
        log.warn("Invalid reset token attempt: {}", e.getMessage());
        return "redirect:/forgot-password?invalid";
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public String handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e, Model model) {
        log.warn("Registration rejected — duplicate email: {}", e.getMessage());
        model.addAttribute("error", "This email is already registered. Please log in.");
        return "register";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSize(MaxUploadSizeExceededException e, Model model,
                                      HttpServletRequest request, HttpServletResponse response) {
        log.warn("File too large: {}", e.getMessage());
        if (RequestPaths.isApi(request)) {
            return errorRenderer.render(request, response, 413, null,
                    "File too large. Maximum allowed size is 10 MB.");
        }
        model.addAttribute("error", "File too large. Maximum allowed size is 10 MB.");
        return "index";
    }

    @ExceptionHandler(AuthenticationException.class)
    public String handleAuthError(AuthenticationException e, Model model) {
        log.warn("Authentication error: {}", e.getMessage());
        model.addAttribute("error", "Invalid email or password.");
        return "login";
    }

    @ExceptionHandler(BindException.class)
    public Object handleValidation(BindException e, Model model,
                                   HttpServletRequest request, HttpServletResponse response) {
        String msg = e.getBindingResult().getAllErrors().stream()
            .findFirst()
            .map(err -> err.getDefaultMessage())
            .orElse("Invalid data");
        if (RequestPaths.isApi(request)) {
            return errorRenderer.render(request, response, 400, "invalid_request", msg);
        }
        model.addAttribute("error", msg);
        return "register";
    }

    @ExceptionHandler(InvalidPlanException.class)
    public Object handleInvalidPlan(InvalidPlanException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Checkout rejected: {}", e.getMessage());
        return errorRenderer.render(request, response, 400, "invalid_plan",
                "Unknown plan. Use STARTER, PRO or PREMIUM.");
    }

    @ExceptionHandler(PaymentException.class)
    public Object handlePayment(PaymentException e, HttpServletRequest request, HttpServletResponse response) {
        log.error("Payment error: {}", e.getMessage(), e);
        return errorRenderer.render(request, response, 502, null,
                "Checkout could not be started. Please try again in a moment.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatus(ResponseStatusException e,
                                       HttpServletRequest request, HttpServletResponse response) {
        log.warn("Handled error: {} {}", e.getStatusCode().value(), e.getReason());
        return errorRenderer.render(request, response, e.getStatusCode().value(), null, e.getReason());
    }

    /**
     * Everything else. Spring's own web exceptions (unknown static file, wrong method,
     * missing parameter, unreadable body...) carry their status; anything unexpected is a 500.
     */
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            errorResponse.getHeaders().forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
            if (status >= 500) {
                log.error("Unhandled error", e);
                return errorRenderer.render(request, response, status, null, null);
            }
            log.debug("Request error {}: {}", status, e.getMessage());
            // Spring's details ("Required parameter 'x' is not present.") are safe to show,
            // but a 404 keeps the default text that points to the sitemap and llms.txt.
            String detail = status == 404 ? null : errorResponse.getBody().getDetail();
            return errorRenderer.render(request, response, status, null, detail);
        }
        log.error("Unhandled error", e);
        return errorRenderer.render(request, response, 500, null, null);
    }
}
