package com.resumetailor.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Digital Asset Links for the Android app (a Trusted Web Activity).
 *
 * Android only opens the site full screen, without the browser bar, when this
 * file lists the app's package and the SHA-256 fingerprint of its signing key.
 * With Play App Signing that is the key shown in Play Console under
 * Test and release > App integrity, not the local upload key.
 *
 * Configured with APP_ANDROID_PACKAGE_NAME and APP_ANDROID_SHA256_FINGERPRINTS
 * (comma separated). Without fingerprints the route returns 404.
 */
@RestController
public class AssetLinksController {

    private final String packageName;
    private final List<String> fingerprints;

    public AssetLinksController(
            @Value("${app.android.package-name:com.cvfitai.app}") String packageName,
            @Value("${app.android.sha256-fingerprints:}") String fingerprints) {
        this.packageName = packageName;
        this.fingerprints = Arrays.stream(fingerprints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> assetLinks() {
        if (fingerprints.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", packageName,
                        "sha256_cert_fingerprints", fingerprints))));
    }
}
