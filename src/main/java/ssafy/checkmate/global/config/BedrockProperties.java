package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 반입 규정 검사(30번)가 쓰는 Bedrock 설정.
 *
 * 자격증명은 따로 받지 않고 AWS 기본 자격증명 체인(EC2 IAM 역할, 로컬 ~/.aws)을 쓴다 —
 * S3 업로드와 달리 이 기능은 EC2에서만 도는 서버 기능이라 키를 따로 둘 이유가 없다.
 *
 * {@code maxTokens}는 기본값에 맡기지 않고 명시적으로 잡는다. 기본값이 작아 응답 뒷부분이
 * 잘리는 것이 파싱 실패의 주원인이다 — 묶음 하나당 60~80토큰으로 보고 넉넉히 잡는다.
 */
@ConfigurationProperties(prefix = "bedrock")
public record BedrockProperties(
        String region,
        String knowledgeBaseId,
        String modelId,
        Integer numberOfResults,
        Integer maxTokens
) {

    public boolean isConfigured() {
        return knowledgeBaseId != null && !knowledgeBaseId.isBlank();
    }
}
