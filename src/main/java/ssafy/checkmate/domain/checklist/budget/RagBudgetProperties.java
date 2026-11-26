package ssafy.checkmate.domain.checklist.budget;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag-budget")
public record RagBudgetProperties(
        boolean enabled,
        String redisUrl,
        String redisToken,
        long dailyLimitKrw,
        long effectiveKrwPerUsd,
        long retrieveMaxMicroUsd,
        int maxInputTokens,
        int maxRerankUnits,
        String countTokensRegion,
        boolean testApiEnabled,
        String testApiKey
) {
    public RagBudgetProperties {
        if (enabled && (dailyLimitKrw <= 0 || dailyLimitKrw > 1_000_000
                || effectiveKrwPerUsd <= 0 || effectiveKrwPerUsd > 10_000
                || retrieveMaxMicroUsd <= 0 || retrieveMaxMicroUsd > 1_000_000
                || maxInputTokens <= 0 || maxInputTokens > 1_000_000
                || maxRerankUnits <= 0 || maxRerankUnits > 1_000
                || countTokensRegion == null || countTokensRegion.isBlank())) {
            throw new IllegalArgumentException("RAG 예산 한도, 환산율, 검색 단가와 토큰·리랭크 상한을 확인하세요.");
        }
        if (testApiEnabled && (!enabled || testApiKey == null || testApiKey.length() < 32)) {
            throw new IllegalArgumentException("테스트 API에는 RAG 예산 활성화와 32자 이상의 전용 키가 필요합니다.");
        }
    }
}
