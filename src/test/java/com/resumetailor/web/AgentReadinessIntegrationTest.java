package com.resumetailor.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.payment.repository.OrderRepository;
import com.resumetailor.payment.repository.StripeProcessedEventRepository;
import com.resumetailor.repository.PasswordResetTokenRepository;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real server (security, MVC and the container's error dispatch, which
 * MockMvc does not do) without a database, and checks what browsers and agents get.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "stripe.secret-key=sk_test_agent_readiness",
        "stripe.webhook-secret=whsec_agent_readiness",
        "stripe.success-url=http://localhost/credits",
        "stripe.cancel-url=http://localhost/credits",
        "app.base-url=http://localhost",
        "app.mail.from=test@example.com",
        "openai.api.key=test-key",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test",
        "spring.mail.test-connection=false",
        "management.health.mail.enabled=false",
})
class AgentReadinessIntegrationTest {

    private static final String MARKDOWN = "text/markdown";
    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private ResumeHistoryRepository resumeHistoryRepository;
    @MockitoBean private PasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean private OrderRepository orderRepository;
    @MockitoBean private StripeProcessedEventRepository stripeProcessedEventRepository;
    @MockitoBean private JavaMailSender mailSender;

    @LocalServerPort private int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    // ── Real 404s ────────────────────────────────────────────────────────────

    @Test
    void unknownPathIsA404WithMarkdownForAgents() throws Exception {
        HttpResponse<String> response = get("/some-path-that-does-not-exist", MARKDOWN);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contentType(response)).startsWith("text/markdown");
        assertThat(response.headers().allValues("Vary")).anyMatch(v -> v.contains("Accept"));
        assertThat(response.headers().firstValue("Set-Cookie")).as("no session for a 404").isEmpty();
        assertThat(response.body()).startsWith("# Page not found (404)")
                .contains("/some-path-that-does-not-exist", "https://cvfitai.com/llms.txt",
                        "https://cvfitai.com/sitemap.xml");
    }

    @Test
    void unknownPathIsA404PageForBrowsers() throws Exception {
        HttpResponse<String> response = get("/some-path-that-does-not-exist", BROWSER_ACCEPT);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(contentType(response)).startsWith("text/html");
        assertThat(response.body()).contains("Page not found", "ERROR 404", "manifest.webmanifest");
    }

    @Test
    void missingStaticFileIsA404() throws Exception {
        assertThat(get("/css/missing.css", BROWSER_ACCEPT).statusCode()).isEqualTo(404);
        assertThat(get("/error", BROWSER_ACCEPT).statusCode()).isEqualTo(404);
    }

    @Test
    void existingPrivatePagesStillRedirectToTheLoginForm() throws Exception {
        for (String path : List.of("/credits", "/history", "/account", "/history/5/text", "/download/x.pdf")) {
            HttpResponse<String> response = get(path, BROWSER_ACCEPT);
            assertThat(response.statusCode()).as(path).isEqualTo(302);
            assertThat(response.headers().firstValue("Location").orElseThrow()).as(path).endsWith("/login");
        }
    }

    // ── JSON errors on the API ───────────────────────────────────────────────

    @Test
    void apiErrorsAreProblemJsonWhateverTheAcceptHeader() throws Exception {
        for (String accept : new String[] {BROWSER_ACCEPT, MARKDOWN, "*/*"}) {
            HttpResponse<String> unknown = get("/api/nope", accept);
            assertProblem(unknown, 404, "not_found");

            HttpResponse<String> notSignedIn = get("/api/payment/orders/1", accept);
            assertProblem(notSignedIn, 401, "authentication_required");
            assertThat(notSignedIn.headers().firstValue("Location")).isEmpty();
        }
    }

    @Test
    void postWithoutCsrfTokenExplainsTheToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/payment/checkout"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"planId\":\"PRO\"}")));

        JsonNode problem = assertProblem(response, 403, "csrf_token_invalid");
        assertThat(problem.get("hint").asText()).contains("X-CSRF-TOKEN");
    }

    @Test
    void htmlErrorsKeepTheirPagesOutsideTheApi() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/account/delete"))
                .header("Accept", BROWSER_ACCEPT)
                .POST(HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(contentType(response)).startsWith("text/html");
        assertThat(response.body()).contains("Request refused");
    }

    // ── Markdown content negotiation ─────────────────────────────────────────

    @Test
    void homeAndAboutNegotiateMarkdown() throws Exception {
        for (String path : List.of("/", "/about")) {
            HttpResponse<String> markdown = get(path, MARKDOWN);
            assertThat(markdown.statusCode()).as(path).isEqualTo(200);
            assertThat(contentType(markdown)).as(path).isEqualTo("text/markdown;charset=UTF-8");
            assertThat(markdown.headers().allValues("Vary")).as(path).anyMatch(v -> v.contains("Accept"));
            assertThat(markdown.body()).as(path).startsWith("# ").doesNotContain("<html");

            HttpResponse<String> html = get(path, BROWSER_ACCEPT);
            assertThat(html.statusCode()).as(path).isEqualTo(200);
            assertThat(contentType(html)).as(path).startsWith("text/html");
            assertThat(html.headers().allValues("Vary")).as(path).anyMatch(v -> v.contains("Accept"));
            assertThat(html.body()).as(path).contains("<html");
        }
    }

    @Test
    void headRequestForMarkdownHasNoBody() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/"))
                .header("Accept", MARKDOWN)
                .method("HEAD", HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).startsWith("text/markdown");
        assertThat(response.body()).isEmpty();
    }

    // ── Machine-readable files ───────────────────────────────────────────────

    @Test
    void agentFilesArePublic() throws Exception {
        Map<String, String> files = Map.of(
                "/robots.txt", "text/plain",
                "/sitemap.xml", "application/xml",
                "/llms.txt", "text/plain",
                "/openapi.json", "application/json");
        for (Map.Entry<String, String> file : files.entrySet()) {
            HttpResponse<String> response = get(file.getKey(), "*/*");
            assertThat(response.statusCode()).as(file.getKey()).isEqualTo(200);
            assertThat(contentType(response)).as(file.getKey()).startsWith(file.getValue());
        }

        assertThat(get("/robots.txt", "*/*").body()).contains("Sitemap: https://cvfitai.com/sitemap.xml");
        assertThat(get("/llms.txt", "*/*").body()).startsWith("# CV Fit AI\n\n> ").contains("## Docs");
    }

    @Test
    void sitemapListsOnlyPublicPagesThatAnswer200() throws Exception {
        String sitemap = get("/sitemap.xml", "*/*").body();
        List<String> locations = new ArrayList<>();
        for (String part : sitemap.split("<loc>")) {
            if (part.contains("</loc>")) locations.add(part.substring(0, part.indexOf("</loc>")));
        }
        assertThat(locations).isNotEmpty();
        for (String location : locations) {
            String path = location.replace("https://cvfitai.com", "");
            assertThat(get(path, BROWSER_ACCEPT).statusCode()).as(location).isEqualTo(200);
        }
    }

    @Test
    void openApiSpecIsValidAndMatchesTheControllers() throws Exception {
        String spec = get("/openapi.json", "application/json").body();

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(spec);
        assertThat(result.getMessages()).as("OpenAPI validation messages").isEmpty();
        assertThat(result.getOpenAPI().getOpenapi()).isEqualTo("3.1.0");

        JsonNode paths = json.readTree(spec).get("paths");
        Set<RequestMappingInfo> mappings = handlerMapping.getHandlerMethods().keySet();
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> path = it.next();
            for (Iterator<String> methods = path.getValue().fieldNames(); methods.hasNext(); ) {
                RequestMethod method = RequestMethod.valueOf(methods.next().toUpperCase());
                boolean mapped = mappings.stream().anyMatch(info ->
                        info.getPatternValues().contains(path.getKey())
                                && info.getMethodsCondition().getMethods().contains(method));
                assertThat(mapped).as(method + " " + path.getKey() + " has a controller").isTrue();
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(contentType(response)).startsWith("application/problem+json");
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("type").asText()).isEqualTo("about:blank");
        assertThat(body.get("detail").asText()).isNotBlank();
        assertThat(body.get("hint").asText()).isNotBlank();
        return body;
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Accept", accept).GET());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("").replace(" ", "");
    }
}
