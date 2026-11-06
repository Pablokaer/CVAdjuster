package com.resumetailor.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAIServiceTest {

    @Test
    void completeResponseIsParsed() {
        OpenAIService service = serviceAnswering("""
                {"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
                 "content":"{\\"tailoredText\\":\\"JANE ROE\\\\nJava\\",\\"keywords\\":[\\"Java\\"],\\"changes\\":[]}"}}],
                 "usage":{"total_tokens":42}}
                """);

        OpenAIService.TailoredResult result = service.tailorResume("resume", "job");

        assertThat(result.tailoredText()).isEqualTo("JANE ROE\nJava");
        assertThat(result.keywords()).hasSize(1);
        assertThat(result.keywords().get(0).isPresent()).isTrue();
    }

    @Test
    void responseCutOffAtTheTokenLimitIsAFailure() {
        OpenAIService service = serviceAnswering("""
                {"choices":[{"index":0,"finish_reason":"length","message":{"role":"assistant",
                 "content":"{\\"tailoredText\\":\\"JANE ROE"}}]}
                """);

        assertThatThrownBy(() -> service.tailorResume("resume", "job"))
                .hasMessageContaining("too long");
        assertThatThrownBy(() -> service.generateCoverLetter("resume", "job"))
                .hasMessageContaining("too long");
    }

    private static OpenAIService serviceAnswering(String body) {
        // The exchange function replaces the HTTP connector, so no request leaves the test.
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()));
        return new OpenAIService(builder, "test-key", "http://localhost/v1/chat/completions",
                "gpt-4o-mini", 4096, 1000, 5);
    }
}
