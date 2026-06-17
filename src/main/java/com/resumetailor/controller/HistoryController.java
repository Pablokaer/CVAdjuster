package com.resumetailor.controller;

import com.resumetailor.model.ResumeHistory;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import com.resumetailor.service.DocxGeneratorService;
import com.resumetailor.service.PdfGeneratorService;
import com.resumetailor.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HistoryController {

    private final ResumeHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final DocxGeneratorService docxGeneratorService;

    @GetMapping("/history")
    public String history(Model model, Authentication authentication) {
        String email = UserService.extractEmail(authentication);
        List<ResumeHistory> entries = userRepository.findByEmail(email)
            .map(historyRepository::findByUserOrderByCreatedAtDesc)
            .orElse(List.of());

        model.addAttribute("history", entries);
        return "history";
    }

    @GetMapping("/history/{id}/text")
    @ResponseBody
    public ResponseEntity<String> getHistoryText(@PathVariable Long id, Authentication authentication) {
        String email = UserService.extractEmail(authentication);
        return historyRepository.findById(id)
            .filter(e -> e.getUser().getEmail().equals(email))
            .map(e -> ResponseEntity.ok(e.getTailoredText()))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history/{id}/download/{format}")
    public ResponseEntity<byte[]> downloadHistoryFile(
        @PathVariable Long id,
        @PathVariable String format,
        Authentication authentication) {

        String normalizedFormat = format == null ? "" : format.toLowerCase();
        if (!normalizedFormat.equals("pdf") && !normalizedFormat.equals("docx")) {
            return ResponseEntity.badRequest().build();
        }

        String email = UserService.extractEmail(authentication);
        return historyRepository.findById(id)
            .filter(e -> e.getUser().getEmail().equals(email))
            .map(e -> buildHistoryDownload(e, normalizedFormat))
            .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> buildHistoryDownload(ResumeHistory entry, String format) {
        try {
            byte[] fileBytes;
            String contentType;

            if (format.equals("docx")) {
                fileBytes = docxGeneratorService.generateDocx(entry.getTailoredText());
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else {
                fileBytes = pdfGeneratorService.generatePdf(entry.getTailoredText());
                contentType = "application/pdf";
            }

            String filename = "resume-tailored-history-" + entry.getId() + "." + format;

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(fileBytes);
        } catch (IOException e) {
            log.error("Could not generate history download for entry {} as {}", entry.getId(), format, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
