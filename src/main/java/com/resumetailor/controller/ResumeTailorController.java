package com.resumetailor.controller;

import com.resumetailor.dto.TailorResponse;
import com.resumetailor.model.ResumeHistory;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.ResumeTailorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResumeTailorController {

    private final ResumeTailorService resumeTailorService;
    private final ResumeHistoryRepository historyRepository;
    private final UserRepository userRepository;

    @GetMapping("/")
    public String index(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", principal.getName());
        }
        return "index";
    }

    @PostMapping("/tailor")
    public String tailorResume(
        @RequestParam("resumeFile") MultipartFile resumeFile,
        @RequestParam("jobDescription") String jobDescription,
        @RequestParam(value = "outputFormat", defaultValue = "pdf") String outputFormat,
        HttpServletRequest httpRequest,
        Principal principal,
        Model model) {

        log.info("Tailor request: file={}, format={}",
            resumeFile.getOriginalFilename(), outputFormat);

        if (resumeFile.isEmpty()) {
            model.addAttribute("error", "Resume file is required.");
            return "index";
        }

        String filename = resumeFile.getOriginalFilename();

        if (filename == null || (!filename.endsWith(".pdf") && !filename.endsWith(".docx"))) {
            model.addAttribute("error", "Invalid format. Please upload a PDF or DOCX file.");
            return "index";
        }

        if (jobDescription == null || jobDescription.trim().isBlank()) {
            model.addAttribute("error", "Job description cannot be empty.");
            return "index";
        }

        TailorResponse response =
            resumeTailorService.tailorResume(resumeFile, jobDescription, outputFormat);

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

            if (principal != null) {
                final String finalPdfUrl  = pdfUrl;
                final String finalDocxUrl = docxUrl;
                final String snippet = jobDescription.length() > 180
                    ? jobDescription.substring(0, 180) + "…"
                    : jobDescription;

                userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                    ResumeHistory entry = new ResumeHistory();
                    entry.setUser(user);
                    entry.setJobDescriptionSnippet(snippet);
                    entry.setTailoredText(response.getTailoredResumeText());
                    entry.setFormat(outputFormat);
                    entry.setPdfFilename(finalPdfUrl.replace("/download/", ""));
                    entry.setDocxFilename(finalDocxUrl.replace("/download/", ""));
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
        @RequestParam(value = "outputFormat", defaultValue = "pdf") String outputFormat) {

        TailorResponse response =
            resumeTailorService.tailorResume(resumeFile, jobDescription, outputFormat);

        return ResponseEntity
            .status(response.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
            .body(response);
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