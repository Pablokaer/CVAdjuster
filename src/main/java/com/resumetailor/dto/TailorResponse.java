package com.resumetailor.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TailorResponse {

    private boolean success;
    private String message;
    private String tailoredResumeText;
    private String downloadUrl;
    private String docxDownloadUrl;
    private String format;
    private List<ChangeHighlight> changes;

    public TailorResponse() {}

    public TailorResponse(boolean success, String message, String tailoredResumeText,
        String downloadUrl, String docxDownloadUrl,
        String format, List<ChangeHighlight> changes) {
        this.success = success;
        this.message = message;
        this.tailoredResumeText = tailoredResumeText;
        this.downloadUrl = downloadUrl;
        this.docxDownloadUrl = docxDownloadUrl;
        this.format = format;
        this.changes = changes;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getTailoredResumeText() { return tailoredResumeText; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getDocxDownloadUrl() { return docxDownloadUrl; }
    public String getFormat() { return format; }
    public List<ChangeHighlight> getChanges() { return changes; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setTailoredResumeText(String tailoredResumeText) { this.tailoredResumeText = tailoredResumeText; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public void setDocxDownloadUrl(String docxDownloadUrl) { this.docxDownloadUrl = docxDownloadUrl; }
    public void setFormat(String format) { this.format = format; }
    public void setChanges(List<ChangeHighlight> changes) { this.changes = changes; }
}