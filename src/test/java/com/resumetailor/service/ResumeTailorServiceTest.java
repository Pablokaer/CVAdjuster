package com.resumetailor.service;

import com.resumetailor.dto.TailorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeTailorServiceTest {

    private static final String RESUME = "JANE ROE\njane@example.com\nEXPERIENCE\nJava developer at Acme";
    private static final String TAILORED = "JANE ROE\njane@example.com\nEXPERIENCE\nSenior Java developer at Acme";
    private static final String JOB = "Senior Java developer with Spring Boot";
    private static final String LETTER = "Dear Hiring Manager,\n\nI am a strong fit.\n\nSincerely,\nJane Roe";

    @Mock private ResumeExtractorService resumeExtractorService;
    @Mock private OpenAIService openAIService;
    @Mock private PdfGeneratorService pdfGeneratorService;
    @Mock private DocxGeneratorService docxGeneratorService;

    @InjectMocks private ResumeTailorService service;

    @TempDir Path tempDir;

    private final MockMultipartFile file =
            new MockMultipartFile("resumeFile", "cv.pdf", "application/pdf", new byte[] {1});

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "generatedFileTtlHours", 24L);
    }

    @Test
    void withoutCoverLetterNeverCallsTheModelForOne() throws Exception {
        stubResumeTailoring();

        TailorResponse response = service.tailorResume(file, JOB, "pdf", false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCoverLetterText()).isNull();
        assertThat(response.isCoverLetterFailed()).isFalse();
        verify(openAIService, never()).generateCoverLetter(anyString(), anyString());
    }

    @Test
    void coverLetterIsWrittenFromTheTailoredResumeAndSavedAsPdfAndDocx() throws Exception {
        stubResumeTailoring();
        when(openAIService.generateCoverLetter(TAILORED, JOB)).thenReturn(LETTER);
        when(pdfGeneratorService.generateLetterPdf(LETTER)).thenReturn(new byte[] {3});
        when(docxGeneratorService.generateLetterDocx(LETTER)).thenReturn(new byte[] {4});

        TailorResponse response = service.tailorResume(file, JOB, "pdf", true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isCoverLetterFailed()).isFalse();
        assertThat(response.getCoverLetterText()).isEqualTo(LETTER);
        assertThat(response.getCoverLetterPdfUrl()).matches("/download/resume-tailored-[a-f0-9-]+-cover-letter\\.pdf");
        assertThat(response.getCoverLetterDocxUrl()).matches("/download/resume-tailored-[a-f0-9-]+-cover-letter\\.docx");

        String pdfName = response.getCoverLetterPdfUrl().replace("/download/", "");
        assertThat(service.getGeneratedFile(pdfName)).containsExactly(3);
    }

    @Test
    void failedCoverLetterStillReturnsTheResume() throws Exception {
        stubResumeTailoring();
        when(openAIService.generateCoverLetter(anyString(), anyString()))
                .thenThrow(new RuntimeException("OpenAI API request failed or timed out."));

        TailorResponse response = service.tailorResume(file, JOB, "pdf", true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTailoredResumeText()).isEqualTo(TAILORED);
        assertThat(response.isCoverLetterFailed()).isTrue();
        assertThat(response.getCoverLetterText()).isNull();
        assertThat(response.getCoverLetterPdfUrl()).isNull();
    }

    @Test
    void failedResumeSkipsTheCoverLetter() throws Exception {
        when(resumeExtractorService.extractText(any())).thenReturn(RESUME);
        when(openAIService.tailorResume(RESUME, JOB)).thenThrow(new RuntimeException("boom"));

        TailorResponse response = service.tailorResume(file, JOB, "pdf", true);

        assertThat(response.isSuccess()).isFalse();
        verify(openAIService, never()).generateCoverLetter(anyString(), anyString());
    }

    @Test
    void cleanupAlsoRemovesExpiredCoverLetterFiles() throws Exception {
        Path letter = Files.write(tempDir.resolve(
                "resume-tailored-0f8fad5b-d9cb-469f-a165-70867728950e-cover-letter.pdf"), new byte[] {1});
        Path unrelated = Files.write(tempDir.resolve("notes.pdf"), new byte[] {1});
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofHours(48)));
        Files.setLastModifiedTime(letter, old);
        Files.setLastModifiedTime(unrelated, old);

        service.cleanupGeneratedFiles();

        assertThat(letter).doesNotExist();
        assertThat(unrelated).exists();
    }

    @Test
    void deleteGeneratedFilesRemovesResumeAndLetterFilesOnly() throws Exception {
        String id = "resume-tailored-0f8fad5b-d9cb-469f-a165-70867728950e";
        List<Path> generated = List.of(
                Files.write(tempDir.resolve(id + ".pdf"), new byte[] {1}),
                Files.write(tempDir.resolve(id + ".docx"), new byte[] {1}),
                Files.write(tempDir.resolve(id + "-cover-letter.pdf"), new byte[] {1}),
                Files.write(tempDir.resolve(id + "-cover-letter.docx"), new byte[] {1}));
        Path other = Files.write(tempDir.resolve("resume-tailored-11111111-2222-3333-4444-555555555555.pdf"), new byte[] {1});

        service.deleteGeneratedFiles(id + ".pdf");
        service.deleteGeneratedFiles("../../etc/passwd");
        service.deleteGeneratedFiles(null);

        generated.forEach(p -> assertThat(p).doesNotExist());
        assertThat(other).exists();
    }

    private void stubResumeTailoring() throws Exception {
        when(resumeExtractorService.extractText(any())).thenReturn(RESUME);
        when(openAIService.tailorResume(RESUME, JOB))
                .thenReturn(new OpenAIService.TailoredResult(TAILORED, List.of(), List.of()));
        when(pdfGeneratorService.generatePdf(TAILORED)).thenReturn(new byte[] {1});
        when(docxGeneratorService.generateDocx(TAILORED)).thenReturn(new byte[] {2});
    }
}
