package com.resumetailor.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class PdfGeneratorService {

    /**
     * Gera um PDF formatado a partir do texto do currículo adaptado.
     *
     * @param resumeText Texto do currículo adaptado
     * @return Bytes do arquivo PDF gerado
     */
    public byte[] generatePdf(String resumeText) throws IOException {
        log.info("Gerando PDF do currículo adaptado...");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);

        document.setMargins(40, 50, 40, 50);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        String[] lines = resumeText.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                document.add(new Paragraph("\n").setFontSize(6));
                continue;
            }

            // Detecta cabeçalhos de seção (linhas em MAIÚSCULAS ou com "---")
            if (isSectionHeader(trimmed)) {
                // Linha separadora visual
                Paragraph separator = new Paragraph()
                        .add(new Text("_".repeat(80)))
                        .setFont(regularFont)
                        .setFontSize(7)
                        .setFontColor(ColorConstants.LIGHT_GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2);
                document.add(separator);

                Paragraph header = new Paragraph()
                        .add(new Text(trimmed.replace("-", "").trim()))
                        .setFont(boldFont)
                        .setFontSize(12)
                        .setFontColor(ColorConstants.DARK_GRAY)
                        .setMarginTop(8)
                        .setMarginBottom(4)
                        .setTextAlignment(TextAlignment.LEFT);
                document.add(header);

            } else if (isNameLine(trimmed, lines, line)) {
                // Primeira linha não vazia = nome do candidato
                Paragraph nameParagraph = new Paragraph()
                        .add(new Text(trimmed))
                        .setFont(boldFont)
                        .setFontSize(18)
                        .setFontColor(ColorConstants.BLACK)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(4);
                document.add(nameParagraph);

            } else if (isContactLine(trimmed)) {
                // Linhas de contato (email, telefone, LinkedIn)
                Paragraph contactParagraph = new Paragraph()
                        .add(new Text(trimmed))
                        .setFont(regularFont)
                        .setFontSize(9)
                        .setFontColor(ColorConstants.DARK_GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2);
                document.add(contactParagraph);

            } else {
                // Texto normal
                Paragraph paragraph = new Paragraph()
                        .add(new Text(trimmed))
                        .setFont(regularFont)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.BLACK)
                        .setMarginBottom(2)
                        .setTextAlignment(TextAlignment.JUSTIFIED);
                document.add(paragraph);
            }
        }

        document.close();
        log.info("PDF gerado com sucesso. {} bytes.", outputStream.size());

        return outputStream.toByteArray();
    }

    /**
     * Gera um PDF de carta (cover letter): parágrafos alinhados à esquerda,
     * sem a formatação de currículo (nome centralizado, cabeçalhos de seção).
     *
     * @param letterText Texto da carta, parágrafos separados por linha em branco
     * @return Bytes do arquivo PDF gerado
     */
    public byte[] generateLetterPdf(String letterText) throws IOException {
        log.info("Gerando PDF da cover letter...");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(60, 60, 60, 60);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        for (String block : letterText.strip().split("\\n\\s*\\n")) {
            document.add(new Paragraph(block.strip())
                    .setFont(regularFont)
                    .setFontSize(11)
                    .setFontColor(ColorConstants.BLACK)
                    .setMultipliedLeading(1.3f)
                    .setMarginBottom(10)
                    .setTextAlignment(TextAlignment.LEFT));
        }

        document.close();
        log.info("PDF da cover letter gerado com sucesso. {} bytes.", outputStream.size());
        return outputStream.toByteArray();
    }

    private boolean isSectionHeader(String line) {
        if (line.startsWith("---") || line.endsWith("---")) return true;
        if (line.length() < 3) return false;

        // Linha em MAIÚSCULAS com pelo menos 3 caracteres
        String letters = line.replaceAll("[^a-zA-Z]", "");
        return !letters.isEmpty() && letters.equals(letters.toUpperCase())
                && line.length() >= 3 && line.length() <= 50;
    }

    private boolean isNameLine(String line, String[] lines, String original) {
        // Heurística: primeira linha não vazia do documento
        for (String l : lines) {
            if (!l.trim().isEmpty()) {
                return l.equals(original);
            }
        }
        return false;
    }

    private boolean isContactLine(String line) {
        return line.contains("@") ||
               line.contains("linkedin.com") ||
               line.matches(".*\\(\\d{2}\\).*\\d{4}.*") ||
               line.matches(".*\\+\\d{2}.*\\d{4}.*");
    }
}
