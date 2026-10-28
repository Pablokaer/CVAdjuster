package com.resumetailor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thymeleaf 3.1.5+ rejects T(...) type references to non-JDK classes at render time
 * ("Access is forbidden for type ..."), which turns the whole page into an error.
 * Nothing catches that at compile time, so guard the templates here.
 */
class TemplateExpressionTest {

    private static final Pattern TYPE_REFERENCE = Pattern.compile("T\\(\\s*([\\w.]+)\\s*\\)");

    @Test
    void templatesOnlyReferenceJdkTypes() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".html"))
                    .flatMap(p -> forbiddenReferences(p).stream())
                    .toList();

            assertThat(offenders).as("T(...) references to non-JDK types").isEmpty();
        }
    }

    private static List<String> forbiddenReferences(Path template) {
        try {
            Matcher m = TYPE_REFERENCE.matcher(Files.readString(template));
            return m.results()
                    .map(r -> r.group(1))
                    .filter(type -> !type.startsWith("java."))
                    .map(type -> template.getFileName() + ": " + type)
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + template, e);
        }
    }
}
