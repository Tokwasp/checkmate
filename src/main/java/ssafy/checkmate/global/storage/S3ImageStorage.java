package ssafy.checkmate.global.storage;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ssafy.checkmate.domain.checklist.exception.ImageUploadFailedException;
import ssafy.checkmate.global.config.StorageProperties;

@Slf4j
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public String upload(MultipartFile image, String directory) {
        String key = directory + "/" + UUID.randomUUID() + StorageFileName.extensionOf(image.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(image.getContentType())
                            .build(),
                    RequestBody.fromInputStream(image.getInputStream(), image.getSize()));
        } catch (IOException | SdkException e) {
            log.error("S3 업로드 실패 key={}", key, e);
            throw new ImageUploadFailedException();
        }
        return baseUrl() + "/" + key;
    }

    private String baseUrl() {
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            return trimTrailingSlash(properties.baseUrl());
        }
        return "https://%s.s3.%s.amazonaws.com".formatted(properties.bucket(), properties.region());
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
