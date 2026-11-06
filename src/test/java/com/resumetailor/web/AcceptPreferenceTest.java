package com.resumetailor.web;

import com.resumetailor.web.AcceptPreference.Format;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptPreferenceTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            // Agents asking for Markdown
            "text/markdown                                                     | MARKDOWN",
            "text/markdown;q=0.9, text/html;q=0.8                              | MARKDOWN",
            "text/markdown, text/html                                          | MARKDOWN",
            "text/x-markdown                                                   | MARKDOWN",
            "text/markdown, */*;q=0.1                                          | MARKDOWN",
            // Browsers and generic clients keep HTML
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8  | HTML",
            "text/html, text/markdown;q=0.5                                    | HTML",
            "*/*                                                               | HTML",
            "NULL                                                              | HTML",
            "not a media type                                                  | HTML",
            // JSON clients
            "application/json                                                  | JSON",
            "application/problem+json                                          | JSON",
            "application/json, text/html                                       | HTML",
            "text/markdown;q=0, application/json                               | JSON",
    })
    void picksTheFormatTheClientPrefers(String accept, Format expected) {
        assertThat(AcceptPreference.of(accept)).isEqualTo(expected);
    }
}
