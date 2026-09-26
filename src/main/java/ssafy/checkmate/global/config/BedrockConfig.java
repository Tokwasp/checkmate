package ssafy.checkmate.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import ssafy.checkmate.domain.checklist.service.QuarantineReranker;

/**
 * Bedrock SDK 클라이언트 빈. 라이브러리 자원은 쓰는 쪽이 만들지 않고 여기서 등록한다.
 *
 * 클라이언트를 만드는 것만으로는 자격증명을 요구하지 않는다(호출 시점에 해석된다) —
 * 그래서 자격증명이 없는 CI에서도 컨텍스트는 정상적으로 뜬다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({BedrockProperties.class, QuarantineRerankProperties.class})
public class BedrockConfig {

    @Bean
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient(BedrockProperties properties) {
        warnIfNotConfigured(properties);
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    /**
     * 리랭크 모델이 도쿄 리전에만 있어 지식베이스 검색용과 다른 리전의 클라이언트를 쓴다. 같은 타입 빈을
     * 둘 두면 주입이 모호해지므로 클라이언트를 빈으로 노출하지 않고 {@link QuarantineReranker} 안에 넣는다.
     */
    @Bean
    public QuarantineReranker quarantineReranker(QuarantineRerankProperties properties) {
        BedrockAgentRuntimeClient rerankClient = BedrockAgentRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .build();
        return new QuarantineReranker(rerankClient, properties);
    }

    private void warnIfNotConfigured(BedrockProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("bedrock.knowledge-base-id가 비어 있어 반입 규정 검사(30번)가 동작하지 않는다. .env를 확인할 것");
        }
    }
}
