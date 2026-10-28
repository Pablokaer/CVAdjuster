package com.resumetailor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class DocxGeneratorService {

    /**
     * Gera um arquivo DOCX formatado a partir do texto do currículo adaptado.
     *
     * @param resumeText Texto do currículo adaptado
     * @return Bytes do arquivo DOCX gerado
     */
    public byte[] generateDocx(String resumeText) throws IOException {
        log.info("Gerando DOCX do currículo adaptado...");

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            String[] lines = resumeText.split("\n");
            boolean isFirstNonEmpty = true;

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) {
                    document.createParagraph(); // Linha em branco
                    continue;
                }

                if (isFirstNonEmpty) {
                    // Nome do candidato - centralizado e grande
                    addNameParagraph(document, trimmed);
                    isFirstNonEmpty = false;

                } else if (isContactLine(trimmed)) {
                    addContactParagraph(document, trimmed);

                } else if (isSectionHeader(trimmed)) {
                    addSectionHeader(document, trimmed.replace("-", "").trim());

                } else {
                    addBodyParagraph(document, trimmed);
                }
            }

            document.write(outputStream);
            log.info("DOCX gerado com sucesso. {} bytes.", outputStream.size());
            return outputStream.toByteArray();
        }
    }

    /**
     * Gera um DOCX de carta (cover letter): parágrafos alinhados à esquerda,
     * sem a formatação de currículo (nome centralizado, cabeçalhos de seção).
     *
     * @param letterText Texto da carta, parágrafos separados por linha em branco
     * @return Bytes do arquivo DOCX gerado
     */
    public byte[] generateLetterDocx(String letterText) throws IOException {
        log.info("Gerando DOCX da cover letter...");

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            for (String block : letterText.strip().split("\\n\\s*\\n")) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                paragraph.setSpacingAfter(200);

                XWPFRun run = paragraph.createRun();
                run.setFontSize(11);
                run.setFontFamily("Calibri");

                // Linhas dentro do mesmo bloco (ex.: "Sincerely," + nome) viram quebras de linha
                String[] lines = block.strip().split("\\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) run.addBreak();
                    run.setText(lines[i].strip(), i);
                }
            }

            document.write(outputStream);
            log.info("DOCX da cover letter gerado com sucesso. {} bytes.", outputStream.size());
            return outputStream.toByteArray();
        }
    }

    private void addNameParagraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(100);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(20);
        run.setFontFamily("Calibri");
    }

    private void addContactParagraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(50);

        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(10);
        run.setFontFamily("Calibri");
        run.setColor("666666");
    }

    private void addSectionHeader(XWPFDocument doc, String text) {
        // Linha separadora
        XWPFParagraph separator = doc.createParagraph();
        separator.setSpacingBefore(200);
        XWPFRun sepRun = separator.createRun();
        sepRun.addBreak();

        // Cabeçalho da seção
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingAfter(100);

        // Borda inferior para separar visualmente
        paragraph.setBorderBottom(Borders.SINGLE);

        XWPFRun run = paragraph.createRun();
        run.setText(text.toUpperCase());
        run.setBold(true);
        run.setFontSize(12);
        run.setFontFamily("Calibri");
        run.setColor("333333");
    }

    private void addBodyParagraph(XWPFDocument doc, String text) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingAfter(80);

        XWPFRun run = paragraph.createRun();

        // Detecta se é uma linha de destaque (cargo, empresa, etc.)
        if (text.startsWith("•") || text.startsWith("-") || text.startsWith("*")) {
            paragraph.setIndentationLeft(360);
            run.setText(text);
        } else {
            run.setText(text);
        }

        run.setFontSize(11);
        run.setFontFamily("Calibri");
    }

    private boolean isSectionHeader(String line) {
        if (line.startsWith("---") || line.endsWith("---")) return true;
        if (line.length() < 3) return false;

        String letters = line.replaceAll("[^a-zA-Z]", "");
        return !letters.isEmpty()
                && letters.equals(letters.toUpperCase())
                && line.length() >= 3
                && line.length() <= 50;
    }

    private boolean isContactLine(String line) {
        return line.contains("@")
               || line.contains("linkedin.com")
               || line.matches(".*\\(\\d{2}\\).*\\d{4}.*")
               || line.matches(".*\\+\\d{2}.*\\d{4}.*");
    }
}
