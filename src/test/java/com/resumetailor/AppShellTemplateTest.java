package com.resumetailor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every page must include fragments/app-shell. Besides the manifest and service
 * worker, it is what hides credit purchases inside the Android app, which Google
 * Play requires; a page without it would show buy buttons in the app.
 */
class AppShellTemplateTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void everyPageIncludesTheAppShell() throws Exception {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<String> missing = files
                    .filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !p.startsWith(TEMPLATES.resolve("fragments")))
                    .filter(p -> !read(p).contains("~{fragments/app-shell :: head}"))
                    .map(p -> TEMPLATES.relativize(p).toString())
                    .toList();

            assertThat(missing).as("pages without the app-shell fragment").isEmpty();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + p, e);
        }
    }
}
