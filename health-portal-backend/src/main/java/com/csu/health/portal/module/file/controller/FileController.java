package com.csu.health.portal.module.file.controller;

import com.csu.health.portal.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class FileController {

    @Value("${app.upload.path}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.')) : "";
        String filename = UUID.randomUUID() + ext;
        Path dir = Path.of(uploadPath);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);
        file.transferTo(target.toFile());
        return Result.ok(Map.of("url", "/uploads/" + filename, "name", original == null ? filename : original));
    }
}
