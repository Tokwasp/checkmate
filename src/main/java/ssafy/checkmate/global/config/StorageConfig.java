package ssafy.checkmate.global.config;

import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import ssafy.checkmate.global.storage.ImageStorage;
import ssafy.checkmate.global.storage.LocalImageStorage;
import ssafy.checkmate.global.storage.S3ImageStorage;

@Slf4j
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(StorageProperties properties) {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            log.warn("storage.bucket이 비어 있어 이미지를 로컬({})에 저장한다. 운영에서는 S3 설정을 채울 것", properties.localDir());
            return new LocalImageStorage(Path.of(properties.localDir()));
        }
        return new S3ImageStorage(s3Client(properties), properties);
    }

    private S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.accessKey() != null && !properties.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        }
        return builder.build();
    }
}
