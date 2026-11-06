package com.resumetailor.web;

import com.resumetailor.payment.entity.CreditPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownPagesTest {

    private final MarkdownPages pages = new MarkdownPages();

    @Test
    void homeAndAboutAreAvailableWithEveryPlaceholderFilled() {
        for (String path : new String[] {"/", "/about"}) {
            String markdown = pages.forPath(path).orElseThrow();
            assertThat(markdown).startsWith("# ").doesNotContain("{{").doesNotContain("}}");
        }
        assertThat(pages.forPath("/login")).isEmpty();
        assertThat(pages.hasPage("/privacy")).isFalse();
    }

    @Test
    void pricesComeFromTheCreditPlans() {
        String home = pages.forPath("/").orElseThrow();
        for (CreditPlan plan : CreditPlan.values()) {
            assertThat(home).contains("**" + plan.getDisplayName() + "**: " + plan.getDisplayPrice()
                    + " for " + plan.getCreditsToAdd() + " credits");
        }
        assertThat(home).contains("Each resume uses " + CreditPlan.CREDITS_PER_RESUME + " credit");
    }

    @Test
    void homePointsAgentsToTheirDocs() {
        String home = pages.forPath("/").orElseThrow();
        assertThat(home).contains("https://cvfitai.com/llms.txt", "https://cvfitai.com/openapi.json",
                "https://cvfitai.com/about");
    }
}
