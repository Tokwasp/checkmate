package ssafy.checkmate.global.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.checklist.exception.ImageUploadFailedException;

/**
 * S3 설정이 없을 때 쓰는 대체 저장소. 파일을 로컬 디렉터리에 저장하고
 * {@code /uploads/...} 경로를 돌려준다(WebConfig가 이 경로를 정적 리소스로 서빙한다).
 * 서버를 여러 대 띄우면 인스턴스마다 파일이 흩어지므로 운영에서는 S3 설정을 채워야 한다.
 */
@Slf4j
public class LocalImageStorage implements ImageStorage {

    public static final String URL_PREFIX = "/uploads";

    private final Path root;

    public LocalImageStorage(Path root) {
        this.root = root;
    }

    @Override
    public String upload(MultipartFile image, String directory) {
        String fileName = UUID.randomUUID() + StorageFileName.extensionOf(image.getOriginalFilename());
        try {
            Path targetDirectory = root.resolve(directory);
            Files.createDirectories(targetDirectory);
            image.transferTo(targetDirectory.resolve(fileName));
        } catch (IOException | IllegalStateException e) {
            log.error("로컬 이미지 저장 실패 directory={} fileName={}", directory, fileName, e);
            throw new ImageUploadFailedException();
        }
        return "%s/%s/%s".formatted(URL_PREFIX, directory, fileName);
    }
}
