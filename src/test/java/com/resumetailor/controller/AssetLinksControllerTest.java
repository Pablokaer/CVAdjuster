package com.resumetailor.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetLinksControllerTest {

    @Test
    void notFoundUntilFingerprintsAreConfigured() {
        assertThat(new AssetLinksController("com.cvfitai.app", " ").assetLinks().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsThePackageAndEveryFingerprint() {
        ResponseEntity<List<Map<String, Object>>> response =
                new AssetLinksController("com.cvfitai.app", "AA:BB, CC:DD").assetLinks();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> statement = response.getBody().get(0);
        assertThat(statement.get("relation")).isEqualTo(List.of("delegate_permission/common.handle_all_urls"));

        Map<String, Object> target = (Map<String, Object>) statement.get("target");
        assertThat(target.get("namespace")).isEqualTo("android_app");
        assertThat(target.get("package_name")).isEqualTo("com.cvfitai.app");
        assertThat(target.get("sha256_cert_fingerprints")).isEqualTo(List.of("AA:BB", "CC:DD"));
    }
}
