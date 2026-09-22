package ssafy.checkmate.domain.checklist.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
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

    /** 같은 패키지의 테스트가 실제로 나가는 문구를 그대로 확인한다. */
    static final String SYSTEM_PROMPT = """
            당신은 각 나라의 항공 보안 규정 문서를 찾기 위한 검색어를 만드는 전문가입니다.

            한국어 물품 이름 목록을 받아 각각을 지정된 언어의 검색어로 옮기고, 아래 JSON 객체 하나만
            출력하세요. 설명·코드펜스·머리말은 쓰지 마세요.

            {"translations": {"한국어 이름": "현지어 검색어"}}

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
     * 빈 결과일 때 한국어로만 검색하게 되는 건 부르는 쪽이 경고로 남긴다.
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
            log.error("[security-check] 번역 재시도까지 실패 — 한국어로만 검색한다 country={}", jurisdiction, second);
            return Map.of();
        }
    }

    private Map<String, String> ask(List<String> itemNames, CountryJurisdiction jurisdiction) {
        return parse(gmsClient.chat(SYSTEM_PROMPT, userPrompt(itemNames, jurisdiction)), itemNames);
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
        TranslationDto dto = objectMapper.readValue(GmsResponses.stripCodeFence(content), TranslationDto.class);
        if (dto.getTranslations() == null) {
            throw new IllegalStateException("번역 응답에 translations가 없습니다.");
        }

        Map<String, String> translations = new LinkedHashMap<>();
        itemNames.forEach(name -> {
            String terms = dto.getTranslations().get(name);
            if (terms != null && !terms.isBlank()) {
                translations.put(name, terms.trim());
            }
        });
        return translations;
    }

    private static long elapsedMillisSince(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class TranslationDto {
        private Map<String, String> translations;
    }
}
