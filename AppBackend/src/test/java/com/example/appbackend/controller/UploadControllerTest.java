package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.qcloud.cos.COSClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UploadControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void missingCosConfigurationFallsBackToPublicLocalUpload() throws Exception {
        COSClient cosClient = mock(COSClient.class);
        UploadController controller = new UploadController(cosClient);
        ReflectionTestUtils.setField(controller, "bucket", "");
        ReflectionTestUtils.setField(controller, "domain", "");
        ReflectionTestUtils.setField(controller, "uploadPrefix", "smart-campus/media");
        ReflectionTestUtils.setField(controller, "mapBuildingsPrefix", "smart-campus/map-buildings");
        ReflectionTestUtils.setField(controller, "localUploadDir", tempDir.toString());
        ReflectionTestUtils.setField(controller, "fileBaseUrl", "http://localhost:8080/");
        MockMultipartFile image = new MockMultipartFile(
                "file", "cover.png", "image/png", new byte[]{1, 2, 3});

        Result<Map<String, String>> result = controller.uploadImage(
                image, null, new MockHttpServletRequest());

        assertEquals(200, result.getCode());
        String url = result.getData().get("url");
        assertTrue(url.startsWith("http://localhost:8080/uploads/smart-campus/media/"));
        String relative = url.substring("http://localhost:8080/uploads/".length());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(tempDir.resolve(relative)));
        verifyNoInteractions(cosClient);
    }

    @Test
    void uploadsMultipleAiResourcesWithStructuredMetadata() throws Exception {
        COSClient cosClient = mock(COSClient.class);
        UploadController controller = localController(cosClient);
        MockMultipartFile image = new MockMultipartFile(
                "files", "campus.png", "image/png", new byte[]{1, 2});
        MockMultipartFile document = new MockMultipartFile(
                "files", "notes.pdf", "application/pdf", new byte[]{3, 4, 5});

        Result<List<Map<String, Object>>> result = controller.uploadResources(List.of(image, document));

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals("image", result.getData().get(0).get("type"));
        assertEquals("document", result.getData().get(1).get("type"));
        assertEquals("notes.pdf", result.getData().get(1).get("name"));
        assertTrue(String.valueOf(result.getData().get(1).get("url"))
                .contains("/uploads/smart-campus/ai-resources/"));
        verifyNoInteractions(cosClient);
    }

    @Test
    void rejectsUnsupportedAiResourceBeforeWriting() throws Exception {
        COSClient cosClient = mock(COSClient.class);
        UploadController controller = localController(cosClient);
        MockMultipartFile executable = new MockMultipartFile(
                "file", "unsafe.exe", "application/octet-stream", new byte[]{1});

        Result<Map<String, Object>> result = controller.uploadResource(executable);

        assertEquals(400, result.getCode());
        assertTrue(Files.list(tempDir).findAny().isEmpty());
        verifyNoInteractions(cosClient);
    }

    private UploadController localController(COSClient cosClient) {
        UploadController controller = new UploadController(cosClient);
        ReflectionTestUtils.setField(controller, "bucket", "");
        ReflectionTestUtils.setField(controller, "domain", "");
        ReflectionTestUtils.setField(controller, "uploadPrefix", "smart-campus/media");
        ReflectionTestUtils.setField(controller, "mapBuildingsPrefix", "smart-campus/map-buildings");
        ReflectionTestUtils.setField(controller, "localUploadDir", tempDir.toString());
        ReflectionTestUtils.setField(controller, "fileBaseUrl", "http://localhost:8080/");
        return controller;
    }
}
