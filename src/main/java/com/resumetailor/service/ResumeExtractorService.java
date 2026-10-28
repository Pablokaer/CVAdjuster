package com.resumetailor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ResumeExtractorService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Upper bounds for parsing untrusted files — a resume never needs more than this
    private static final int MAX_PDF_PAGES = 20;
    private static final long MAX_PDF_MEMORY = 64L * 1024 * 1024;
    private static final long MAX_DOCX_ENTRY_SIZE = 50L * 1024 * 1024;
    private static final long MAX_DOCX_TEXT_SIZE = 1_000_000;
    private static final long EXTRACTION_TIMEOUT_SECONDS = 30;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    // The PDF spec lets the header appear anywhere in the first 1024 bytes
    private static final int PDF_HEADER_SEARCH_LIMIT = 1024;

    static {
        // Zip-bomb protection for DOCX (global POI settings; this app only reads untrusted OOXML here)
        ZipSecureFile.setMinInflateRatio(0.02);
        ZipSecureFile.setMaxEntrySize(MAX_DOCX_ENTRY_SIZE);
        ZipSecureFile.setMaxTextSize(MAX_DOCX_TEXT_SIZE);
        ZipSecureFile.setMaxFileCount(500);
    }

    private final AsyncTaskExecutor extractionExecutor;

    public ResumeExtractorService(@Qualifier("extractionTaskExecutor") AsyncTaskExecutor extractionExecutor) {
        this.extractionExecutor = extractionExecutor;
    }

    /**
     * Extracts plain text from an uploaded resume file.
     * Supports both PDF (.pdf) and Word (.docx) formats.
     *
     * @param file the uploaded resume file
     * @return extracted and cleaned plain text
     * @throws IOException if the file cannot be read
     */
    public String extractText(MultipartFile file) throws IOException {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        boolean isPdf = originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        byte[] bytes = file.getBytes();
        validateContent(bytes, isPdf);

        log.info("Extracting text from resume file: {}", sanitizeForLog(originalFilename));

        String raw = runWithTimeout(() -> isPdf ? extractFromPdf(bytes) : extractFromDocx(bytes));

        String cleaned = cleanText(raw);
        log.info("Text extracted successfully. {} characters.", cleaned.length());
        return cleaned;
    }

    private String extractFromPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes, "", null, null,
                MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_MEMORY).streamCache)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new IllegalArgumentException(
                        "The PDF has too many pages. Please upload a resume with at most " + MAX_PDF_PAGES + " pages.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Password-protected PDFs are not supported.");
        }
    }

    private String extractFromDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private interface Extraction {
        String run() throws IOException;
    }

    /**
     * Parses on a bounded pool so a malicious file can neither hang the request
     * nor tie up more than a few threads at once.
     */
    private String runWithTimeout(Extraction extraction) throws IOException {
        Future<String> future;
        try {
            future = extractionExecutor.submit(extraction::run);
        } catch (RejectedExecutionException e) {
            throw new IllegalArgumentException("The server is busy processing other resumes. Please try again in a moment.");
        }

        try {
            return future.get(EXTRACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Resume extraction timed out after {}s", EXTRACTION_TIMEOUT_SECONDS);
            throw new IllegalArgumentException("The file took too long to process. Please upload a simpler PDF or DOCX.");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Resume extraction interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException iae) throw iae;
            if (cause instanceof IOException ioe) throw ioe;
            throw new IOException("Could not read the resume file", cause);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The resume file cannot be empty.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Could not determine the file name.");
        }

        if (!hasSupportedExtension(filename)) {
            throw new IllegalArgumentException(
                    "Unsupported file format. Please upload a PDF (.pdf) or Word (.docx) file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("The file exceeds the 10MB size limit.");
        }
    }

    /**
     * Checks the file signature so the extension cannot lie about the content.
     */
    private void validateContent(byte[] bytes, boolean isPdf) {
        boolean valid = isPdf ? containsPdfHeader(bytes) : startsWith(bytes, ZIP_MAGIC);
        if (!valid) {
            throw new IllegalArgumentException(
                    "The file content does not match its extension. Please upload a valid PDF or DOCX file.");
        }
    }

    private static boolean containsPdfHeader(byte[] bytes) {
        int limit = Math.min(bytes.length, PDF_HEADER_SEARCH_LIMIT) - PDF_MAGIC.length;
        for (int offset = 0; offset <= limit; offset++) {
            if (matchesAt(bytes, PDF_MAGIC, offset)) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && matchesAt(bytes, prefix, 0);
    }

    private static boolean matchesAt(byte[] bytes, byte[] pattern, int offset) {
        for (int i = 0; i < pattern.length; i++) {
            if (bytes[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    public static boolean hasSupportedExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".docx");
    }

    private static String sanitizeForLog(String value) {
        return value.replaceAll("[\\r\\n\\t]", "_");
    }

    private String cleanText(String text) {
        if (text == null) return "";

        return text
                .replaceAll("(\\r?\\n){3,}", "\n\n")
                .lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"))
                .strip();
    }
}
