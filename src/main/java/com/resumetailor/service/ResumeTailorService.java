package com.resumetailor.service;

import com.resumetailor.dto.TailorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeTailorService {

    private final ResumeExtractorService resumeExtractorService;
    private final OpenAIService openAIService;
    private final PdfGeneratorService pdfGeneratorService;
    private final DocxGeneratorService docxGeneratorService;

    @Value("${app.temp-dir}")
    private String tempDir;

    @Value("${app.generated-file-ttl-hours:24}")
    private long generatedFileTtlHours;

    /**
     * Orchestrates the full flow: extraction → AI tailoring → file generation.
     *
     * @param resumeFile     uploaded resume (PDF or DOCX)
     * @param jobDescription full job posting text
     * @param outputFormat   primary output format: "pdf" or "docx"
     * @return TailorResponse with download URLs
     */
    public TailorResponse tailorResume(MultipartFile resumeFile, String jobDescription, String outputFormat) {
        try {
            // 1. Extract text from uploaded resume (PDF or DOCX)
            log.info("Step 1: Extracting text from resume...");
            String resumeText = resumeExtractorService.extractText(resumeFile);

            if (resumeText.isBlank()) {
                return TailorResponse.builder()
                        .success(false)
                        .message("Could not extract text from the file. " +
                                 "For PDFs, ensure it is not a scanned image. " +
                                 "For DOCX, ensure the document contains selectable text.")
                        .build();
            }

            int wordCount = resumeText.trim().isEmpty() ? 0 : resumeText.trim().split("\\s+").length;
            if (wordCount > 2000) {
                return TailorResponse.builder()
                        .success(false)
                        .message("Your resume exceeds the 2,000-word limit (" + wordCount + " words detected). " +
                                 "Please shorten it before submitting.")
                        .build();
            }

            // 2. Call OpenAI API to tailor the resume
            log.info("Step 2: Tailoring resume with AI...");
            OpenAIService.TailoredResult tailored = openAIService.tailorResume(resumeText, jobDescription);

            // 3. Generate both PDF and DOCX output files
            log.info("Step 3: Generating output files...");
            String fileId = UUID.randomUUID().toString();
            String primaryUrl = saveGeneratedFiles(tailored.tailoredText(), fileId, outputFormat);
            String docxUrl    = buildDocxUrl(primaryUrl);

            return TailorResponse.builder()
                    .success(true)
                    .message("Resume tailored successfully!")
                    .tailoredResumeText(tailored.tailoredText())
                    .downloadUrl(primaryUrl)
                    .docxDownloadUrl(docxUrl)
                    .format(outputFormat)
                    .changes(tailored.changes())
                    .keywords(tailored.keywords())
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Validation error: {}", e.getMessage());
            return TailorResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error while processing resume", e);
            return TailorResponse.builder()
                    .success(false)
                    .message("An internal error occurred while processing your resume. Please try again.")
                    .build();
        }
    }

    /**
     * Always generates both PDF and DOCX; returns URL for the primary format.
     */
    private String saveGeneratedFiles(String text, String fileId, String format) throws IOException {
        Path dir = Paths.get(tempDir);
        Files.createDirectories(dir);

        String docxFilename = "resume-tailored-" + fileId + ".docx";
        String pdfFilename  = "resume-tailored-" + fileId + ".pdf";

        Files.write(dir.resolve(docxFilename), docxGeneratorService.generateDocx(text));
        Files.write(dir.resolve(pdfFilename),  pdfGeneratorService.generatePdf(text));

        log.info("Generated: {} and {}", pdfFilename, docxFilename);

        return "docx".equalsIgnoreCase(format)
                ? "/download/" + docxFilename
                : "/download/" + pdfFilename;
    }

    /**
     * Derives the DOCX download URL from the primary URL regardless of extension.
     */
    public String buildDocxUrl(String primaryUrl) {
        String base = primaryUrl
                .replace("/download/", "")
                .replaceAll("\\.(pdf|docx)$", "");
        return "/download/" + base + ".docx";
    }

    /**
     * Reads a previously generated file for download.
     */
    public byte[] getGeneratedFile(String filename) throws IOException {
        Path filePath = Paths.get(tempDir).resolve(filename);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    @Scheduled(
            fixedDelayString = "${app.generated-file-cleanup-ms:3600000}",
            initialDelayString = "${app.generated-file-cleanup-initial-delay-ms:3600000}"
    )
    public void cleanupGeneratedFiles() {
        Path dir = Paths.get(tempDir);
        if (!Files.isDirectory(dir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(Duration.ofHours(generatedFileTtlHours));

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isGeneratedResumeFile)
                    .filter(path -> isOlderThan(path, cutoff))
                    .forEach(this::deleteGeneratedFile);
        } catch (IOException e) {
            log.warn("Could not clean generated resume files in {}", dir, e);
        }
    }

    private boolean isGeneratedResumeFile(Path path) {
        String filename = path.getFileName().toString();
        return filename.matches("resume-tailored-[a-fA-F0-9-]+\\.(pdf|docx)");
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            log.warn("Could not read modified time for generated file {}", path, e);
            return false;
        }
    }

    private void deleteGeneratedFile(Path path) {
        try {
            Files.deleteIfExists(path);
            log.info("Deleted expired generated resume file: {}", path.getFileName());
        } catch (IOException e) {
            log.warn("Could not delete generated resume file {}", path, e);
        }
    }
}
