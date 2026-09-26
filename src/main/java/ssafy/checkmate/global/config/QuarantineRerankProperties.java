package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 촬영 물품 반입 판정(35번)의 <b>품목 질의</b>에만 쓰는 리랭크 설정. 과태료 질의와 항공 규정 검사는
 * {@link BedrockProperties#numberOfResults()} 그대로 벡터 검색만 한다.
 *
 * 품목 질의를 {@code candidatePool}개로 넓게 받아 리랭크하고, 점수 {@code minScore} 이상 중 상위
 * {@code topN}개만 판정에 넣는다. 리랭크 모델은 도쿄 리전에만 있어 {@code region}을 따로 둔다.
 */
@ConfigurationProperties(prefix = "bedrock.quarantine-rerank")
public record QuarantineRerankProperties(
        boolean enabled,
        String region,
        String modelArn,
        int candidatePool,
        int topN,
        double minScore
) {
}
