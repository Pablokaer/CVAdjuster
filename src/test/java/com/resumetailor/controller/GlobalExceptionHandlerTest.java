package com.resumetailor.controller;

import com.resumetailor.payment.exception.InvalidPlanException;
import com.resumetailor.payment.exception.PaymentException;
import com.resumetailor.web.ErrorRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ErrorRenderer());
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void unknownPlanIsA400WithItsOwnCode() {
        Map<String, Object> problem = problem(handler.handleInvalidPlan(
                new InvalidPlanException("GOLD"), request("/api/payment/checkout"), response), 400);

        assertThat(problem).containsEntry("code", "invalid_plan");
        assertThat((String) problem.get("detail")).contains("STARTER, PRO or PREMIUM");
    }

    @Test
    void paymentProviderFailureIsA502WithoutTheInternalMessage() {
        Map<String, Object> problem = problem(handler.handlePayment(
                new PaymentException("Failed to create Stripe checkout session: sk_live_secret"),
                request("/api/payment/checkout"), response), 502);

        assertThat(problem).containsEntry("code", "upstream_unavailable");
        assertThat(problem.toString()).doesNotContain("sk_live_secret");
    }

    @Test
    void missingOrderIsAJson404() {
        Map<String, Object> problem = problem(handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."),
                request("/api/payment/orders/9"), response), 404);

        assertThat(problem).containsEntry("detail", "Order not found.").containsEntry("code", "not_found");
    }

    @Test
    void tooLargeUploadIsJsonOnTheApiAndTheFormPageOnTheWebsite() {
        MaxUploadSizeExceededException tooLarge = new MaxUploadSizeExceededException(10);

        problem(handler.handleMaxUploadSize(tooLarge, new ExtendedModelMap(), request("/api/tailor"), response), 413);

        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(handler.handleMaxUploadSize(tooLarge, model, request("/tailor"), response)).isEqualTo("index");
        assertThat(model.getAttribute("error")).isEqualTo("File too large. Maximum allowed size is 10 MB.");
    }

    @Test
    void validationErrorsAreJsonOnTheApiAndTheRegisterPageOnTheWebsite() {
        BindException invalid = new BindException(new BeanPropertyBindingResult(new Object(), "request"));
        invalid.reject("blank", "Plan ID is required");

        Map<String, Object> problem = problem(handler.handleValidation(
                invalid, new ExtendedModelMap(), request("/api/payment/checkout"), response), 400);
        assertThat(problem).containsEntry("detail", "Plan ID is required");

        assertThat(handler.handleValidation(invalid, new ExtendedModelMap(), request("/register"), response))
                .isEqualTo("register");
    }

    @Test
    void missingPageIsA404InTheRequestedFormat() {
        MockHttpServletRequest agent = request("/nope");
        agent.addHeader("Accept", "text/markdown");
        Object markdown = handler.handleGenericException(
                new NoResourceFoundException(HttpMethod.GET, "nope"), agent, response);

        assertThat(markdown).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) markdown;
        assertThat(entity.getStatusCode().value()).isEqualTo(404);
        assertThat(entity.getHeaders().getContentType().toString()).startsWith("text/markdown");
        assertThat((String) entity.getBody()).startsWith("# Page not found (404)");

        Object html = handler.handleGenericException(
                new NoResourceFoundException(HttpMethod.GET, "nope"), request("/nope"), new MockHttpServletResponse());
        assertThat(((ModelAndView) html).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void wrongMethodIsA405WithTheAllowHeader() {
        handler.handleGenericException(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("POST")), request("/api/tailor"), response);

        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void unexpectedErrorIsA500PageInsteadOfA200() {
        Object html = handler.handleGenericException(new IllegalStateException("db password is hunter2"),
                request("/history"), response);

        ModelAndView page = (ModelAndView) html;
        assertThat(page.getViewName()).isEqualTo("error");
        assertThat(page.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(page.getModel().toString()).doesNotContain("hunter2");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> problem(Object result, int status) {
        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) result;
        assertThat(entity.getStatusCode().value()).isEqualTo(status);
        assertThat(entity.getHeaders().getContentType()).hasToString("application/problem+json");
        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        assertThat(body).containsEntry("status", status).containsKeys("type", "title", "detail", "code", "hint");
        return body;
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
