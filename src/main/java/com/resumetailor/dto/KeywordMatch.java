package com.resumetailor.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeywordMatch {
    private String keyword;
    private boolean present;
}
