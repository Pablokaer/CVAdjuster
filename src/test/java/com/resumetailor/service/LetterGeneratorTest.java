package com.resumetailor.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LetterGeneratorTest {

    private static final String LETTER = """
            Dear Hiring Manager,

            I am applying for the Senior Java Developer role.

            At Acme I led the migration to Spring Boot.

            Sincerely,
            Jane Roe
            """;

    @Test
    void docxKeepsOneParagraphPerBlockAndLineBreaksInside() throws Exception {
        byte[] bytes = new DocxGeneratorService().generateLetterDocx(LETTER);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> paragraphs = doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();

            assertThat(paragraphs).containsExactly(
                    "Dear Hiring Manager,",
                    "I am applying for the Senior Java Developer role.",
                    "At Acme I led the migration to Spring Boot.",
                    "Sincerely,\nJane Roe");
        }
    }

    @Test
    void pdfContainsTheWholeLetter() throws Exception {
        byte[] bytes = new PdfGeneratorService().generateLetterPdf(LETTER);

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);

            assertThat(text)
                    .contains("Dear Hiring Manager,")
                    .contains("At Acme I led the migration to Spring Boot.")
                    .contains("Sincerely,")
                    .contains("Jane Roe");
        }
    }
}
