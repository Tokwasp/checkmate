package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 저장소 설정. bucket이 비어 있으면 S3 대신 로컬 디렉터리에 저장한다.
 * accessKey/secretKey가 비어 있으면 AWS 기본 자격증명 체인(EC2 IAM 역할 등)을 쓴다.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String localDir,
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        String baseUrl
) {
}
