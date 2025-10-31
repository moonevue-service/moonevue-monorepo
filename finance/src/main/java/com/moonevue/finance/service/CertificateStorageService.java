package com.moonevue.finance.service;

import com.moonevue.finance.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateStorageService {

    private final StorageProperties storageProperties;

    public String storeCertificate(Long tenantId, Long configurationId, MultipartFile file) throws IOException {
        String baseDir = storageProperties.getCertsDir();
        Path dir = Paths.get(baseDir, tenantId.toString(), configurationId.toString()).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "cert.p12" : file.getOriginalFilename());
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safeName = "cert-" + timestamp + "-" + UUID.randomUUID() + ext;

        Path dest = dir.resolve(safeName);
        try (var in = file.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        // Restrição de permissão básica (Unix-like). Ignorado se não suportado.
        try {
            Files.setPosixFilePermissions(dest, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) { }

        return dest.toString();
    }
}
