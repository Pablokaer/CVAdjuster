package com.resumetailor.controller;

import com.resumetailor.dto.TailorResponse;
import com.resumetailor.exception.InsufficientCreditsException;
import com.resumetailor.model.ResumeHistory;
import com.resumetailor.payment.entity.CreditPlan;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.ResumeExtractorService;
import com.resumetailor.service.ResumeTailorService;
import com.resumetailor.service.UserService;
import com.resumetailor.util.InputSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResumeTailorController {

    private final ResumeTailorService resumeTailorService;
    private final ResumeHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            model.addAttribute("user", UserService.extractEmail(authentication));
        }
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            model.addAttribute("user", UserService.extractEmail(authentication));
        }
        return "about";
    }

    @PostMapping("/tailor")
    public String tailorResume(
        @RequestParam("resumeFile") MultipartFile resumeFile,
        @RequestParam("jobDescription") String jobDescription,
        @RequestParam(value = "outputFormat", defaultValue = "pdf") String outputFormat,
        @RequestParam(value = "includeCoverLetter", defaultValue = "false") boolean includeCoverLetter,
        HttpServletRequest httpRequest,
        Authentication authentication,
        Model model) {
        // resolve email regardless of login type (form or Google OAuth2)
        final String principal = UserService.extractEmail(authentication);

        log.info("Tailor request: file={}, format={}, coverLetter={}",
            sanitizeForLog(resumeFile.getOriginalFilename()), outputFormat, includeCoverLetter);

        String validationError = validateTailorInput(resumeFile, jobDescription);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            return "index";
        }

        // Sanitize the input so raw content is never injected into prompts
        String sanitizedJobDescription = InputSanitizer.sanitizeJobDescription(jobDescription);

        TailorResponse response;
        try {
            // pass sanitized text to the tailoring service to avoid injecting raw content into prompts
            response = runPaidTailoring(principal, resumeFile, sanitizedJobDescription, outputFormat, includeCoverLetter);
        } catch (InsufficientCreditsException e) {
            model.addAttribute("error", insufficientCreditsMessage(e, includeCoverLetter));
            model.addAttribute("noCredits", true);
            return "index";
        }

        // Refresh credit balance so the result page shows the post-deduction value.
        // GlobalModelAttributes runs before this handler, so we must override here.
        if (principal != null) {
            model.addAttribute("userCredits", userService.getCredits(principal));
        }

        if (response.isSuccess()) {

            String baseUrl = getBaseUrl(httpRequest);

            String downloadUrl = response.getDownloadUrl();
            String docxUrl = response.getDocxDownloadUrl();
            String docxPublicUrl = baseUrl + docxUrl;

            model.addAttribute("success", true);
            model.addAttribute("tailoredText", response.getTailoredResumeText());

            String pdfUrl = downloadUrl.endsWith(".pdf")
                ? downloadUrl
                : docxUrl.replace(".docx", ".pdf");

            model.addAttribute("downloadPdfUrl", pdfUrl);
            model.addAttribute("downloadDocxUrl", docxUrl);

            model.addAttribute("googleDocsUrl",
                "https://docs.google.com/viewer?url=" +
                    java.net.URLEncoder.encode(docxPublicUrl, java.nio.charset.StandardCharsets.UTF_8));

            model.addAttribute("docxPublicUrl", docxPublicUrl);
            model.addAttribute("format", response.getFormat());
            model.addAttribute("changes", response.getChanges());
            model.addAttribute("keywords", response.getKeywords());
            model.addAttribute("coverLetterText", response.getCoverLetterText());
            model.addAttribute("coverLetterPdfUrl", response.getCoverLetterPdfUrl());
            model.addAttribute("coverLetterDocxUrl", response.getCoverLetterDocxUrl());
            model.addAttribute("coverLetterFailed", response.isCoverLetterFailed());

            if (principal != null) {
                final String finalPdfUrl  = pdfUrl;
                final String finalDocxUrl = docxUrl;
                final String snippet = sanitizedJobDescription.length() > 180
                    ? sanitizedJobDescription.substring(0, 180) + "…"
                    : sanitizedJobDescription;

                userRepository.findByEmail(principal).ifPresent(user -> {
                    ResumeHistory entry = new ResumeHistory();
                    entry.setUser(user);
                    entry.setJobDescriptionSnippet(snippet);
                    entry.setTailoredText(response.getTailoredResumeText());
                    entry.setFormat(outputFormat);
                    entry.setPdfFilename(finalPdfUrl.replace("/download/", ""));
                    entry.setDocxFilename(finalDocxUrl.replace("/download/", ""));
                    entry.setCoverLetterText(response.getCoverLetterText());
                    historyRepository.save(entry);
                });
            }

        } else {
            model.addAttribute("error", response.getMessage());
        }

        return "result";
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String filename) {
        try {

            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }

            if (!filename.matches("[a-zA-Z0-9._-]+")) {
                return ResponseEntity.badRequest().build();
            }

            byte[] fileBytes = resumeTailorService.getGeneratedFile(filename);

            String contentType;

            if (filename.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (filename.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileBytes);

        } catch (IOException e) {
            log.error("Erro ao baixar arquivo: {}", filename, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/open-in-gdocs/{filename}")
    public ResponseEntity<Void> openInGoogleDocs(
        @PathVariable String filename,
        HttpServletRequest request) {

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        if (!filename.matches("[a-zA-Z0-9._-]+")) {
            return ResponseEntity.badRequest().build();
        }

        String baseUrl = getBaseUrl(request);
        String docxUrl = baseUrl + "/download/" + filename;

        String encodedUrl = java.net.URLEncoder.encode(docxUrl, java.nio.charset.StandardCharsets.UTF_8);

        String googleDocsUrl = "https://docs.google.com/viewer?url=" + encodedUrl;

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(googleDocsUrl))
            .build();
    }

    @PostMapping("/api/tailor")
    @ResponseBody
    public ResponseEntity<TailorResponse> tailorResumeApi(
        @RequestParam("resumeFile") MultipartFile resumeFile,
        @RequestParam("jobDescription") String jobDescription,
        @RequestParam(value = "outputFormat", defaultValue = "pdf") String outputFormat,
        @RequestParam(value = "includeCoverLetter", defaultValue = "false") boolean includeCoverLetter,
        Authentication authentication) {

        final String principal = UserService.extractEmail(authentication);

        String validationError = validateTailorInput(resumeFile, jobDescription);
        if (validationError != null) {
            return ResponseEntity.badRequest()
                .body(TailorResponse.builder().success(false).message(validationError).build());
        }

        String sanitizedJobDescription = InputSanitizer.sanitizeJobDescription(jobDescription);

        // Same billing rules as the web form
        TailorResponse response;
        try {
            response = runPaidTailoring(principal, resumeFile, sanitizedJobDescription, outputFormat, includeCoverLetter);
        } catch (InsufficientCreditsException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(TailorResponse.builder().success(false)
                    .message(insufficientCreditsMessage(e, includeCoverLetter)).build());
        }

        return ResponseEntity
            .status(response.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
            .body(response);
    }

    /**
     * Charges up front, runs the generation and refunds what was not delivered:
     * everything if the resume fails, or only the cover letter's credit if just
     * the cover letter fails.
     *
     * @throws InsufficientCreditsException if the balance does not cover the request
     */
    private TailorResponse runPaidTailoring(String principal, MultipartFile resumeFile, String jobDescription,
                                            String outputFormat, boolean includeCoverLetter) {
        int cost = CreditPlan.CREDITS_PER_RESUME
            + (includeCoverLetter ? CreditPlan.CREDITS_PER_COVER_LETTER : 0);
        userService.deductCredits(principal, cost);

        TailorResponse response =
            resumeTailorService.tailorResume(resumeFile, jobDescription, outputFormat, includeCoverLetter);

        if (!response.isSuccess()) {
            // User should not be charged for a failed attempt
            userService.refundCredits(principal, cost);
        } else if (response.isCoverLetterFailed()) {
            userService.refundCredits(principal, CreditPlan.CREDITS_PER_COVER_LETTER);
        }
        return response;
    }

    private static String insufficientCreditsMessage(InsufficientCreditsException e, boolean includeCoverLetter) {
        if (!includeCoverLetter) {
            return e.getMessage();
        }
        int cost = CreditPlan.CREDITS_PER_RESUME + CreditPlan.CREDITS_PER_COVER_LETTER;
        return "A resume with a cover letter uses " + cost + " credits. "
            + "Add credits, or uncheck the cover letter option.";
    }

    /**
     * Validation shared by the web form and the API.
     *
     * @return a user-facing error message, or {@code null} when the input is valid
     */
    private String validateTailorInput(MultipartFile resumeFile, String jobDescription) {
        if (resumeFile.isEmpty()) {
            return "Resume file is required.";
        }

        String filename = resumeFile.getOriginalFilename();
        if (filename == null || !ResumeExtractorService.hasSupportedExtension(filename)) {
            return "Invalid format. Please upload a PDF or DOCX file.";
        }

        if (jobDescription == null || jobDescription.trim().isBlank()) {
            return "Job description cannot be empty.";
        }

        // Heuristic detection for prompt-injection
        if (InputSanitizer.containsPromptInjection(jobDescription)) {
            return "Job description contains disallowed instructions or suspicious content. Please provide only the job posting text without commands or code.";
        }

        String sanitized = InputSanitizer.sanitizeJobDescription(jobDescription, 0);
        long wordCount = sanitized.isEmpty() ? 0 : sanitized.split("\s+").length;
        if (wordCount > 3000) {
            return "Job description must not exceed 3,000 words (currently " + wordCount + ").";
        }

        return null;
    }

    private static String sanitizeForLog(String value) {
        return value == null ? null : value.replaceAll("[\r\n\t]", "_");
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName();
            if (request.getServerPort() != 80 && request.getServerPort() != 443) {
                host += ":" + request.getServerPort();
            }
        }

        return scheme + "://" + host;
    }
}