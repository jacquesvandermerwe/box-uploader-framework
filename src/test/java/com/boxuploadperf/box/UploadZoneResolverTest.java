package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadZoneResolverTest {

    @Test
    void parsesRegionalUploadUrl() {
        UploadZoneContext zone = UploadZoneResolver.fromPreflightResponse(
                "https://upload-las.app.box.com/api/2.0/files/content", null);
        assertEquals("https://upload-las.app.box.com/api/2.0", zone.uploadBaseUrl());
        assertEquals("upload-las.app.box.com", zone.uploadZoneHost());
        assertEquals("https://upload-las.app.box.com/api/2.0/files/content", zone.preflightUploadUrl());
    }

    @Test
    void parsesGlobalUploadUrl() {
        UploadZoneContext zone = UploadZoneResolver.fromPreflightResponse(
                "https://upload.box.com/api/2.0/files/content", "tok");
        assertEquals("https://upload.box.com/api/2.0", zone.uploadBaseUrl());
        assertEquals("upload.box.com", zone.uploadZoneHost());
        assertEquals("tok", zone.uploadToken());
        assertEquals("https://upload.box.com/api/2.0/files/content", zone.preflightUploadUrl());
    }

    @Test
    void stripsQueryFromPreflightUrl() {
        UploadZoneContext zone = UploadZoneResolver.fromPreflightResponse(
                "https://upload-las.app.box.com/api/2.0/files/content?token=secret", null);
        assertEquals("https://upload-las.app.box.com/api/2.0/files/content", zone.preflightUploadUrl());
    }
}
