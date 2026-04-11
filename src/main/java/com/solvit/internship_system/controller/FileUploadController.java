package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileUploadController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String type) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(MessageResponse.of("File is empty"));
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.')) : "";
            String filename = UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path path = dir.resolve(filename);
            Files.copy(file.getInputStream(), path);
            String url = "/api/files/download/" + filename;
            return ResponseEntity.ok(java.util.Map.of("url", url, "filename", filename));
        } catch (IOException e) {
            log.error("Upload failed", e);
            return ResponseEntity.status(500).body(MessageResponse.of("Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable String filename) {
        try {
            Path path = Paths.get(uploadDir).resolve(filename);
            if (!Files.exists(path)) return ResponseEntity.notFound().build();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.InputStreamResource(
                    Files.newInputStream(path));
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
