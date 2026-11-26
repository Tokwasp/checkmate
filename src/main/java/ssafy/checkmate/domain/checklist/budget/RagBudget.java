package ssafy.checkmate.domain.checklist.budget;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

@Slf4j
@Service
public class RagBudget {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long MICROS_PER_KRW = 1_000_000;
    private static final long RERANK_MICRO_USD_PER_UNIT = 2_000;
    // 요금표의 문서+질의 500토큰 기준으로 UTF-8 바이트를 보수적인 추산에 사용한다.
    private static final int RERANK_CHUNK_SIZE = 500;
    private static final int RERANK_CHUNKS_PER_UNIT = 100;
    private static final String KEY_PREFIX = "checkmate:rag-budget:";

    private final RagBudgetProperties properties;
    private final UpstashRagBudgetStore store;
    private final RagTokenCounter tokenCounter;
    private final Clock clock;

    @Autowired
    RagBudget(RagBudgetProperties properties, UpstashRagBudgetStore store, RagTokenCounter tokenCounter) {
        this(properties, store, tokenCounter, Clock.system(SEOUL));
    }

    RagBudget(RagBudgetProperties properties, UpstashRagBudgetStore store, RagTokenCounter tokenCounter, Clock clock) {
        this.properties = properties;
        this.store = store;
        this.tokenCounter = tokenCounter;
        this.clock = clock.withZone(SEOUL);
    }

    public Request start(String modelId, int maxRetrieveCalls, boolean rerankEnabled, int maxOutputTokens) {
        if (!properties.enabled()) {
            return unmetered();
        }
        return reserve(modelId, maxRetrieveCalls, rerankEnabled, maxOutputTokens, false);
    }

    public static Request unmetered() {
        return new Request(null, null, null, null, null, null, 0, 0, 0, 0);
    }

    private Request reserve(String modelId, int maxRetrieveCalls, boolean rerankEnabled, int maxOutputTokens,
                            boolean simulation) {
        Price price = Price.forModel(modelId);
        int rerankUnits = rerankEnabled ? properties.maxRerankUnits() : 0;
        long maxMicroUsd = maxRetrieveCalls * properties.retrieveMaxMicroUsd()
                + rerankUnits * RERANK_MICRO_USD_PER_UNIT
                + price.tokens(properties.maxInputTokens(), maxOutputTokens);
        long reserved = toMicroKrw(maxMicroUsd);
        LocalDate day = LocalDate.now(clock);
        String key = KEY_PREFIX + (simulation ? "test:" : "") + day;
        String id = "request:" + UUID.randomUUID();
        // 전날 예약이 자정을 지나서 끝나도 같은 일자의 키에 정산한다.
        long expiry = day.plusDays(2).atStartOfDay(SEOUL).toEpochSecond();
        long remaining = store.reserve(key, id, reserved, properties.dailyLimitKrw() * MICROS_PER_KRW, expiry);
        if (remaining == -1) {
            throw RagBudgetException.exhausted();
        }
        if (remaining < 0) {
            throw RagBudgetException.unavailable();
        }
        return new Request(properties, store, tokenCounter, key, id, price,
                maxRetrieveCalls, rerankUnits, maxOutputTokens, reserved);
    }

    Simulation simulate(String modelId, boolean quarantine) {
        int maxOutput = 4_000;
        try (Request request = reserve(modelId, quarantine ? 2 : 1, quarantine, maxOutput, true)) {
            request.chargeRetrieve();
            if (quarantine) {
                request.chargeRerank("술", List.of("면세 용량 규정"));
                request.chargeRetrieve();
            }
            int input = Math.min(properties.maxInputTokens(), quarantine ? 11_313 : 10_000);
            request.reserveGeneration(input, maxOutput);
            request.settleGeneration(input, 1_000, maxOutput);
            long remaining = request.finish();
            return new Simulation(request.key, won(request.reservedMicroKrw),
                    won(toMicroKrw(request.chargedMicroUsd)), won(remaining));
        }
    }

    private long toMicroKrw(long microUsd) {
        return microUsd * properties.effectiveKrwPerUsd();
    }

    private static BigDecimal won(long microKrw) {
        return BigDecimal.valueOf(microKrw, 6).stripTrailingZeros();
    }

    record Simulation(String redisKey, BigDecimal reservedKrw, BigDecimal chargedKrw, BigDecimal remainingKrw) {
    }

    private record Price(long inputMicroUsd, long outputMicroUsd) {
        static Price forModel(String modelId) {
            return switch (modelId) {
                case "global.anthropic.claude-haiku-4-5-20251001-v1:0" -> new Price(1, 5);
                case "global.anthropic.claude-sonnet-4-6" -> new Price(3, 15);
                default -> {
                    log.warn("[rag-budget] 예산 단가를 설정하지 않은 Bedrock 모델 {}", modelId);
                    throw RagBudgetException.unavailable();
                }
            };
        }

        long tokens(int input, int output) {
            return input * inputMicroUsd + output * outputMicroUsd;
        }
    }

    public static final class Request implements AutoCloseable {
        private final RagBudgetProperties properties;
        private final UpstashRagBudgetStore store;
        private final RagTokenCounter tokenCounter;
        private final String key;
        private final String id;
        private final Price price;
        private final int maxRetrieveCalls;
        private final int maxRerankUnits;
        private final int maxOutputTokens;
        private final long reservedMicroKrw;
        private int retrieveCalls;
        private long chargedMicroUsd;
        private long generationCharge;
        private boolean rerankCharged;
        private boolean generationReserved;
        private boolean closed;

        private Request(RagBudgetProperties properties, UpstashRagBudgetStore store, RagTokenCounter tokenCounter,
                        String key, String id, Price price, int maxRetrieveCalls, int maxRerankUnits, int maxOutputTokens, long reservedMicroKrw) {
            this.properties = properties;
            this.store = store;
            this.tokenCounter = tokenCounter;
            this.key = key;
            this.id = id;
            this.price = price;
            this.maxRetrieveCalls = maxRetrieveCalls;
            this.maxRerankUnits = maxRerankUnits;
            this.maxOutputTokens = maxOutputTokens;
            this.reservedMicroKrw = reservedMicroKrw;
        }

        public void chargeRetrieve() {
            if (store == null) {
                return;
            }
            if (retrieveCalls >= maxRetrieveCalls) {
                throw RagBudgetException.inputTooLarge();
            }
            retrieveCalls++;
            chargedMicroUsd += properties.retrieveMaxMicroUsd();
        }

        public void chargeRerank(String query, List<String> candidates) {
            if (store == null || candidates.isEmpty()) {
                return;
            }
            int chunkRoom = RERANK_CHUNK_SIZE - query.getBytes(StandardCharsets.UTF_8).length;
            if (chunkRoom <= 0) {
                throw RagBudgetException.inputTooLarge();
            }
            long chunks = 0;
            for (String candidate : candidates) {
                int bytes = candidate.getBytes(StandardCharsets.UTF_8).length;
                chunks += Math.max(1, Math.ceilDiv(bytes, chunkRoom));
            }
            long units = Math.ceilDiv(chunks, RERANK_CHUNKS_PER_UNIT);
            if (rerankCharged || units > maxRerankUnits) {
                throw RagBudgetException.inputTooLarge();
            }
            rerankCharged = true;
            chargedMicroUsd += units * RERANK_MICRO_USD_PER_UNIT;
        }

        public ConverseResponse converse(BedrockRuntimeClient client, ConverseRequest request) {
            if (store == null) {
                return client.converse(request);
            }
            int outputLimit = request.inferenceConfig().maxTokens();
            reserveGeneration(tokenCounter.count(request), outputLimit);
            ConverseResponse response = client.converse(request);
            TokenUsage usage = response.usage();
            if (usage != null && usage.inputTokens() != null && usage.outputTokens() != null) {
                settleGeneration(usage.inputTokens(), usage.outputTokens(), outputLimit);
            }
            return response;
        }

        private void reserveGeneration(int input, int outputLimit) {
            if (generationReserved || input > properties.maxInputTokens() || outputLimit > maxOutputTokens) {
                throw RagBudgetException.inputTooLarge();
            }
            generationReserved = true;
            generationCharge = price.tokens(input, outputLimit);
            chargedMicroUsd += generationCharge;
        }

        private void settleGeneration(int input, int output, int outputLimit) {
            long actual = price.tokens(input, output);
            long planned = price.tokens(properties.maxInputTokens(), maxOutputTokens);
            if (input < 0 || output < 0 || input > properties.maxInputTokens() || output > outputLimit || actual > planned) {
                chargedMicroUsd += planned - generationCharge;
                log.error("[rag-budget] Bedrock 응답 사용량이 예약 범위를 초과함 input={} output={}", input, output);
                throw RagBudgetException.unavailable();
            }
            chargedMicroUsd += actual - generationCharge;
        }

        private long finish() {
            closed = true;
            long remaining = store.settle(key, id, chargedMicroUsd * properties.effectiveKrwPerUsd());
            if (remaining < 0) {
                throw RagBudgetException.unavailable();
            }
            return remaining;
        }

        @Override
        public void close() {
            if (!closed && store != null) {
                finish();
            }
        }
    }
}
