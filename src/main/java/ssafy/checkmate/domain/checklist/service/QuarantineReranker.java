package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQueryContentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfigurationType;
import ssafy.checkmate.global.config.QuarantineRerankProperties;

/**
 * 검역 품목 질의로 넓게 받은 청크를 리랭크해 판정에 넣을 것만 남긴다.
 *
 * 벡터 검색만으로 자르면 물품 규정이 뒤로 밀리고 화물 수입 절차가 앞에 온다 — 골든셋에서 상아 규정은
 * 벡터 11위, 5kg 이하 멸균 통조림 조문은 23위였고 리랭크 뒤 각각 1위·4위가 됐다.
 *
 * 리랭크 모델이 도쿄 리전에만 있어 그 리전 클라이언트를 따로 받는다({@code BedrockConfig}에서 만든다).
 */
public class QuarantineReranker {

    private final BedrockAgentRuntimeClient rerankClient;
    private final QuarantineRerankProperties properties;

    public QuarantineReranker(BedrockAgentRuntimeClient rerankClient, QuarantineRerankProperties properties) {
        this.rerankClient = rerankClient;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public int candidatePool() {
        return properties.candidatePool();
    }

    /** 점수가 기준 이상인 청크만 리랭크 순서대로 돌려준다. 하나도 없으면 빈 목록이다. */
    public List<String> rerank(String query, List<String> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return rerankClient.rerank(requestOf(query, candidates)).results().stream()
                .filter(result -> result.relevanceScore() >= properties.minScore())
                .map(result -> candidates.get(result.index()))
                .toList();
    }

    private RerankRequest requestOf(String query, List<String> candidates) {
        List<RerankSource> sources = candidates.stream()
                .map(text -> RerankSource.builder()
                        .type(RerankSourceType.INLINE)
                        .inlineDocumentSource(RerankDocument.builder()
                                .type(RerankDocumentType.TEXT)
                                .textDocument(RerankTextDocument.builder().text(text).build())
                                .build())
                        .build())
                .toList();

        return RerankRequest.builder()
                .queries(RerankQuery.builder()
                        .type(RerankQueryContentType.TEXT)
                        .textQuery(RerankTextDocument.builder().text(query).build())
                        .build())
                .sources(sources)
                .rerankingConfiguration(RerankingConfiguration.builder()
                        .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                        .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                .numberOfResults(Math.min(properties.topN(), candidates.size()))
                                .modelConfiguration(BedrockRerankingModelConfiguration.builder()
                                        .modelArn(properties.modelArn())
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
