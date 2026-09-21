package ssafy.checkmate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY GMS 게이트웨이 설정.
 *
 * GMS는 벤더별 경로로 나뉜다 — {@code baseUrl}/{@code model}은 OpenAI 경로(체크리스트 추천, 15번),
 * {@code visionBaseUrl}/{@code visionModel}은 Anthropic 경로(촬영 물품 반입 판정, 35번)다. OpenAI 경로에
 * Claude 모델을 보내면 {@code model_not_found}(404)가 나므로 둘을 합칠 수 없다. 모델도 분리해 두어야
 * 35번이 비싼 비전 모델(Opus)을 쓰더라도 15번이 함께 바뀌지 않는다.
 */
@ConfigurationProperties(prefix = "gms")
public record GmsProperties(
        String baseUrl,
        String apiKey,
        String model,
        String visionBaseUrl,
        String visionModel
) {
}
