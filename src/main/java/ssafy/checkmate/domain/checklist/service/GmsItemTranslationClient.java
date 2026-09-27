package ssafy.checkmate.domain.checklist.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * GMS의 OpenAI 경로({@link GmsClient})로 물품 이름을 현지어 검색어로 옮기는 구현.
 *
 * 이름마다 부르지 않고 <b>한 번에 묶어</b> 보낸다 — 반입 검사 한 번에 물품이 수십 종까지 나오는데
 * 이름마다 왕복하면 그만큼 지연이 쌓인다. 검사 전체에서 번역 호출은 한 번(재시도 포함 최대 두 번)이다.
 *
 * 직역만 받지 않고 <b>규정이 쓰는 분류 용어</b>까지 함께 받는다(칼 → {@code 刃物 ナイフ 凶器}).
 * 규정 문서는 개별 물품명보다 분류명으로 서술되는 경우가 많아, 직역만으로는 정작 그 물품을 다루는
 * 조문이 검색에 안 걸린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmsItemTranslationClient implements ItemTranslationClient {

    /**
     * 물품 하나당 넉넉히 잡는 여유분(토큰) — 실측으로 원인을 찾은 값이다.
     *
     * {@code max_tokens}를 안 보내던 시절엔 게이트웨이 기본 상한에 맡겼는데, 물품이 많은
     * 요청(23종 전체를 한 번에 옮길 때)에서 응답이 중간에 잘려 JSON 파싱이 실패하는 걸
     * 실측으로 재현했다({@code GmsFailureDiagnosisManualIT}, 태국어 23종 요청에서
     * {@code UnexpectedEndOfInputException} 재현). 물품 하나의 답은 분류 용어 2~4개 낱말
     * (지시사항)인데, 실제로는 그보다 긴 스크립트(태국어·일본어)와 JSON 이스케이프까지
     * 고려해 넉넉히 잡는다.
     */
    private static final int TOKENS_PER_ITEM = 80;
    private static final int MIN_TOKENS = 300;
    private static final TypeReference<Map<String, String>> TRANSLATIONS = new TypeReference<>() {
    };

    /** 같은 패키지의 테스트가 실제로 나가는 문구를 그대로 확인한다. */
    static final String SYSTEM_PROMPT = """
            당신은 각 나라의 항공 보안 규정 문서를 찾기 위한 검색어를 만드는 전문가입니다.

            한국어 물품 이름 목록을 받아 각각을 지정된 언어의 검색어로 옮기고, 아래 JSON 객체 하나만
            출력하세요. 설명·코드펜스·머리말은 쓰지 마세요.

            {"한국어 이름": "현지어 검색어", "한국어 이름": "현지어 검색어"}

            규칙
            - 키는 받은 한국어 이름을 그대로 씁니다. 고치거나 빠뜨리지 마세요.
            - 값은 직역에 더해, 그 나라 항공 보안 규정이 그 물품을 부를 때 쓰는 분류 용어까지 함께
              적으세요. 문장이 아니라 띄어쓰기로 이어 붙인 낱말 나열로 씁니다.
              예) 칼(일본어) → "刃物 ナイフ 凶器"
              예) 치약(일본어) → "歯磨き粉 液体物 ジェル類"
              예) 보조배터리(일본어) → "モバイルバッテリー リチウムイオン電池 予備電池"
            - 규정에서 쓰는 분류 용어가 확실하지 않으면 직역만 적으세요. 지어내지 마세요.
            - 낱말은 물품 하나당 2~4개면 충분합니다.
            - 값은 지정된 언어로만 쓰고 한국어를 섞지 마세요.
            """;

    private static final String USER_PROMPT_FORMAT = """
            언어: %s

            물품 이름
            %s
            """;

    private final GmsClient gmsClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> translate(List<String> itemNames, CountryJurisdiction jurisdiction) {
        if (itemNames.isEmpty()) {
            return Map.of();
        }

        long startedAt = System.nanoTime();
        Map<String, String> translations = askOnceMore(itemNames, jurisdiction);

        log.info("[security-check] 현지어 검색어 번역 완료 country={} 언어={} 요청={}종 수신={}종 소요={}ms",
                jurisdiction, jurisdiction.getLanguageName(), itemNames.size(), translations.size(),
                elapsedMillisSince(startedAt));
        return translations;
    }

    /**
     * 실패하면 한 번 더 부르고, 그래도 안 되면 빈 결과를 돌려준다.
     *
     * 현지어가 빠진 물품은 규정을 못 찾은 채 통과로 판정되므로 일시적인 오류로 포기하지 않는다.
     * 그렇다고 검사 자체를 실패시키지도 않는다 — GMS 장애가 안전 기능 전체를 멈추게 된다.
     *
     * 재시도까지 실패하면 <b>[gms-call-fail]</b> 마커로 ERROR 로그를 남긴다 — GMS 호출 자체가
     * (네트워크·파싱 등으로) 실패한 저수준 원인을 가리킨다. 빈 결과를 받은 호출 쪽
     * ({@link SecurityCheckService#translateNames})이 어떤 물품이 한국어로만 검색되는지를
     * 별도 마커(<b>[gms-translation-fail]</b>)로 다시 남기므로, 두 마커를 CloudWatch Logs에서
     * 각각 지표화하면 "GMS가 아예 안 됐다"와 "일부 물품만 못 받았다"를 구분해서 알람을 걸 수
     * 있다.
     */
    private Map<String, String> askOnceMore(List<String> itemNames, CountryJurisdiction jurisdiction) {
        try {
            return ask(itemNames, jurisdiction);
        } catch (RuntimeException first) {
            log.warn("[security-check] 번역 호출 실패 — 한 번 더 시도한다 country={}", jurisdiction, first);
        }
        try {
            return ask(itemNames, jurisdiction);
        } catch (RuntimeException second) {
            log.error("[security-check][gms-call-fail] country={} itemCount={} attempts=2 — 한국어로만 검색한다",
                    jurisdiction, itemNames.size(), second);
            return Map.of();
        }
    }

    /**
     * JSON 모드로 부르고({@link GmsClient#chatJson}), 응답은 이름 → 검색어 한 겹 객체로 받는다.
     *
     * 예전엔 {@code {"translations": {...}}} 두 겹으로 받았는데, gpt-4o-mini가 첫 물품 뒤에서 안쪽
     * 객체를 일찍 닫아 {@code {"translations": {"가위": ".."}, "보조배터리": ".."}}} 처럼 쓰는 일이
     * 실측 20회 중 2~5회 있었다. 프롬프트만 쓰면 남는 {@code }} 때문에 파싱이 실패해 재시도로
     * 1.5~2.5초가 늘었고, JSON 모드만 켜면 문법은 맞지만 두 번째 물품이 바깥으로 빠져 조용히
     * 누락됐다. 겹을 없애면 일찍 닫을 안쪽 객체 자체가 없다.
     */
    private Map<String, String> ask(List<String> itemNames, CountryJurisdiction jurisdiction) {
        String content = gmsClient.chatJson(
                SYSTEM_PROMPT, userPrompt(itemNames, jurisdiction), maxTokensFor(itemNames));
        return parse(content, itemNames);
    }

    private static int maxTokensFor(List<String> itemNames) {
        return Math.max(MIN_TOKENS, itemNames.size() * TOKENS_PER_ITEM);
    }

    static String userPrompt(List<String> itemNames, CountryJurisdiction jurisdiction) {
        String lines = itemNames.stream()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n"));
        return USER_PROMPT_FORMAT.formatted(jurisdiction.getLanguageName(), lines);
    }

    /**
     * 요청하지 않은 이름과 빈 값은 버린다 — 모델이 이름을 바꿔 적거나 없는 물품을 지어내면
     * 엉뚱한 낱말이 검색 질의를 흐린다. 보낸 순서대로 담아 로그가 읽히게 둔다.
     */
    private Map<String, String> parse(String content, List<String> itemNames) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("번역 응답이 비어 있습니다.");
        }
        Map<String, String> received = objectMapper.readValue(GmsResponses.stripCodeFence(content), TRANSLATIONS);

        Map<String, String> translations = new LinkedHashMap<>();
        itemNames.forEach(name -> {
            String terms = received.get(name);
            if (terms != null && !terms.isBlank()) {
                translations.put(name, terms.trim());
            }
        });
        return translations;
    }

    private static long elapsedMillisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
