package com.resumetailor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumetailor.dto.ChangeHighlight;
import com.resumetailor.dto.KeywordMatch;
import com.resumetailor.dto.OpenAIDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OpenAIService {

    public record TailoredResult(String tailoredText, List<ChangeHighlight> changes, List<KeywordMatch> keywords) {}

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIService(
            WebClient.Builder webClientBuilder,
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.url}") String apiUrl,
            @Value("${openai.api.model}") String model,
            @Value("${openai.api.max-tokens}") int maxTokens) {

        this.model = model;
        this.maxTokens = maxTokens;

        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    public TailoredResult tailorResume(String resumeText, String jobDescription) {
        log.info("Sending resume to OpenAI API (model: {})...", model);

        OpenAIRequest request = OpenAIRequest.builder()
                .model(model)
                .maxTokens(maxTokens)
                .responseFormat(new ResponseFormat("json_object"))
                .messages(List.of(
                        Message.builder()
                                .role("system")
                                .content(buildSystemPrompt())
                                .build(),
                        Message.builder()
                                .role("user")
                                .content(buildUserPrompt(resumeText, jobDescription))
                                .build()
                ))
                .build();

        try {
            OpenAIResponse response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAIResponse.class)
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("Empty response from OpenAI API.");
            }

            String rawJson = response.getChoices().get(0).getMessage().getContent();

            log.info("Resume tailored successfully. Tokens used: {}",
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "?");

            return parseResult(rawJson);

        } catch (WebClientResponseException e) {
            log.error("OpenAI API error: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error calling OpenAI API: " + e.getMessage(), e);
        }
    }

    private TailoredResult parseResult(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String tailoredText = root.path("tailoredText").asText();

            List<ChangeHighlight> changes = new ArrayList<>();
            JsonNode changesNode = root.path("changes");
            if (changesNode.isArray()) {
                for (JsonNode node : changesNode) {
                    changes.add(ChangeHighlight.builder()
                            .original(node.path("original").asText())
                            .tailored(node.path("tailored").asText())
                            .reason(node.path("reason").asText())
                            .build());
                }
            }

            List<KeywordMatch> keywords = new ArrayList<>();
            JsonNode keywordsNode = root.path("keywords");
            if (keywordsNode.isArray()) {
                String lowerText = tailoredText.toLowerCase();
                for (JsonNode kw : keywordsNode) {
                    String keyword = kw.asText();
                    boolean present = lowerText.contains(keyword.toLowerCase());
                    keywords.add(KeywordMatch.builder()
                            .keyword(keyword)
                            .present(present)
                            .build());
                }
            }

            return new TailoredResult(tailoredText, changes, keywords);
        } catch (Exception e) {
            log.warn("Could not parse structured JSON response, treating as plain text: {}", e.getMessage());
            return new TailoredResult(rawJson, List.of(), List.of());
        }
    }

    private String buildSystemPrompt() {
        return """
                You are an expert career coach and ATS (Applicant Tracking System) optimization specialist.
                Your task is to tailor a resume for a specific job posting, maximizing ATS keyword coverage \
                while keeping the text natural, readable, and honest.

                Return a JSON object with exactly three fields:
                1. "tailoredText": the full tailored resume as a plain text string \
                (preserve the original structure and all formatting conventions such as ALL-CAPS headers)
                2. "keywords": an array of the most important technical skills, tools, and role-specific terms \
                extracted from the job description (10 to 20 items, plain strings only)
                3. "changes": an array of the most meaningful changes made, each item containing:
                   - "original": the original phrase or sentence (concise, max 200 chars)
                   - "tailored": the new version of that phrase or sentence
                   - "reason": a brief explanation of why this change improves the resume for this specific role

                RULES for tailoring:
                1. Keep the exact same section structure as the original resume
                2. Do NOT invent experiences, certifications or skills that are not in the original
                3. Rewrite every bullet point and description to weave in keywords from the job posting — \
                use the exact phrasing from the job description whenever possible, since ATS systems match exact strings
                4. Every keyword extracted from the job description must appear at least once in the tailored resume \
                (in the skills section, summary, or a bullet point) — as long as the candidate's background supports it
                5. In the skills section, list all matching technical skills first, using the exact names from the job description \
                (e.g. if the JD says "Node.js", use "Node.js", not "NodeJS" or "Node")
                6. Reorder skills to highlight the most relevant ones first
                7. Rewrite the professional summary/objective to open with the job title from the posting and \
                include 3–5 of the most critical keywords within the first two sentences
                8. Keep all original dates, company names and job titles unchanged
                9. Preserve any section separators and formatting conventions (e.g. ALL-CAPS headers)
                10. Integrate keywords naturally into context — avoid keyword-stuffing that reads as a list; \
                embed them into achievement statements and action-verb bullet points

                RULES for the changes list:
                - Include between 5 and 15 of the most impactful changes only
                - Skip trivial changes (punctuation fixes, minor rephrasing)
                - Focus on keyword additions, skill reordering, and summary rewrites
                """;
    }

    private String buildUserPrompt(String resumeText, String jobDescription) {
        return """
                --- ORIGINAL RESUME ---
                %s

                --- JOB DESCRIPTION ---
                %s

                Tailor the resume above for this job posting. \
                Ensure every keyword from the "keywords" list appears naturally in the tailored resume. \
                Return the JSON response.
                """.formatted(resumeText, jobDescription);
    }
}
