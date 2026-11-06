package com.resumetailor.web;

import com.resumetailor.web.ErrorRenderer.ErrorInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorRendererTest {

    @Test
    void problemDetailsFollowRfc9457WithCodeAndHint() {
        Map<String, Object> problem = ErrorRenderer.problem(ErrorInfo.of(404, null, null, "/api/nope"), "/api/nope");

        assertThat(problem).containsEntry("type", "about:blank")
                .containsEntry("title", "Not Found")
                .containsEntry("status", 404)
                .containsEntry("instance", "/api/nope")
                .containsEntry("code", "not_found")
                .containsKeys("detail", "hint", "docs");
    }

    @Test
    void callerCodeAndDetailOverrideTheDefaults() {
        ErrorInfo info = ErrorInfo.of(400, "invalid_plan", "Unknown plan.", "/api/payment/checkout");

        assertThat(info.code()).isEqualTo("invalid_plan");
        assertThat(info.detail()).isEqualTo("Unknown plan.");
        assertThat(info.hint()).contains("openapi.json");
    }

    @Test
    void notFoundMarkdownExplainsTheErrorAndLinksTheDocs() {
        String markdown = ErrorRenderer.markdown(ErrorInfo.of(404, null, null, "/missing"));

        assertThat(markdown).startsWith("# Page not found (404)")
                .contains("There is no page or resource at /missing")
                .contains("https://cvfitai.com/llms.txt", "https://cvfitai.com/sitemap.xml",
                        "https://cvfitai.com/openapi.json");
    }

    @Test
    void unknownStatusesStillGetACodeAndText() {
        ErrorInfo teapot = ErrorInfo.of(418, null, null, "/");
        assertThat(teapot.code()).isEqualTo("i_m_a_teapot");
        assertThat(teapot.detail()).isNotBlank();

        assertThat(ErrorInfo.of(500, null, null, "/").code()).isEqualTo("internal_error");
    }

    @Test
    void echoedPathsCannotInjectMarkup() {
        assertThat(RequestPaths.safe("/a`b<script>\n")).isEqualTo("/abscript");
        assertThat(RequestPaths.safe("/" + "x".repeat(500))).hasSize(203).endsWith("...");
        assertThat(RequestPaths.safe(null)).isEqualTo("/");
    }
}
