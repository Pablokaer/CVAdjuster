package com.resumetailor.web;

import com.resumetailor.payment.entity.CreditPlan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Markdown versions of the public content pages, served to clients that send
 * {@code Accept: text/markdown}. The texts live in {@code resources/markdown/};
 * prices and credit costs are filled in from {@link CreditPlan} so they never
 * disagree with the HTML pages.
 */
@Component
public class MarkdownPages {

    private static final Map<String, String> FILES = Map.of(
            "/", "markdown/home.md",
            "/about", "markdown/about.md");

    private final Map<String, String> pages;

    public MarkdownPages() {
        this.pages = FILES.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> fill(read(e.getValue()))));
    }

    public Optional<String> forPath(String path) {
        return Optional.ofNullable(pages.get(path));
    }

    public boolean hasPage(String path) {
        return pages.containsKey(path);
    }

    static String fill(String template) {
        return template
                .replace("{{resumeCost}}", credits(CreditPlan.CREDITS_PER_RESUME))
                .replace("{{coverLetterCost}}", credits(CreditPlan.CREDITS_PER_COVER_LETTER))
                .replace("{{plans}}", plans());
    }

    private static String plans() {
        return Stream.of(CreditPlan.values())
                .map(plan -> "- **%s**: %s for %s (%s)%s".formatted(
                        plan.getDisplayName(), plan.getDisplayPrice(), credits(plan.getCreditsToAdd()),
                        plan.getCostPerResume(), plan.isFeatured() ? ", most popular" : ""))
                .collect(Collectors.joining("\n"));
    }

    private static String credits(int count) {
        return count + (count == 1 ? " credit" : " credits");
    }

    private static String read(String location) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing Markdown page " + location, e);
        }
    }
}
