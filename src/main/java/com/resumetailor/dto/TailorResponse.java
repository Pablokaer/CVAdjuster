package com.resumetailor.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TailorResponse {

    private boolean success;
    private String message;
    private String tailoredResumeText;
    private String downloadUrl;
    private String docxDownloadUrl;
    private String format;
    private List<ChangeHighlight> changes;
    private List<KeywordMatch> keywords;

    /** Present only when a cover letter was requested and generated. */
    private String coverLetterText;
    private String coverLetterPdfUrl;
    private String coverLetterDocxUrl;

    /**
     * True when a cover letter was requested but could not be generated. The resume
     * itself still succeeded, so only the cover letter's credit is refunded.
     */
    private boolean coverLetterFailed;
}
