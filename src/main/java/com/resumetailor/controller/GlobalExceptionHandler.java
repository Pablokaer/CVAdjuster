package com.resumetailor.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException e, Model model) {
        log.warn("File too large: {}", e.getMessage());
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
    public String handleValidation(BindException e, Model model) {
        String msg = e.getBindingResult().getAllErrors().stream()
            .findFirst()
            .map(err -> err.getDefaultMessage())
            .orElse("Invalid data");
        model.addAttribute("error", msg);
        return "register";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatus(ResponseStatusException e, Model model) {
        log.warn("Handled error: {}", e.getReason());
        model.addAttribute("error", e.getReason());
        return "error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResource(NoResourceFoundException e, Model model) {
        model.addAttribute("error", "Page not found.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception e, Model model) {
        log.error("Unhandled error", e);
        model.addAttribute("error", "An unexpected error occurred. Please try again.");
        return "error";
    }
}
