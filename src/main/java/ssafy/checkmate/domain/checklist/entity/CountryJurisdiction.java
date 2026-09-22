package ssafy.checkmate.domain.checklist.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 반입 규정 검사(30번)에서 {@code Trip.country}를 지식베이스 필터 코드로 바꾸는 매핑표.
 *
 * 코드는 S3 버킷 {@code checkmate-regulation-docs}의 국가 프리픽스, 그리고 각 문서
 * 메타데이터의 {@code jurisdictions} 값과 같다. 코퍼스가 이 11개국만 다루므로 매핑에
 * 없는 국가는 거절한다 — 필터 없이 검색하면 엉뚱한 나라 규정이 섞이기 때문이다.
 *
 * {@link #GLOBAL_CODE}는 국가가 아니라 IATA 리튬배터리 지침처럼 모든 국가에 공통으로
 * 적용되는 문서에 붙은 값이다. 그래서 검색 필터에는 항상 국가 코드와 함께 넣는다.
 */
public enum CountryJurisdiction {

    KR("한국어", "대한민국", "한국"),
    JP("일본어", "일본"),
    CN("중국어 간체", "중국"),
    TW("중국어 번체", "대만"),
    TH("태국어", "태국"),
    VN("베트남어", "베트남"),
    PH("영어", "필리핀"),
    US("영어", "미국", "미합중국"),
    GB("영어", "영국"),
    FR("프랑스어", "프랑스"),
    IT("이탈리아어", "이탈리아");

    /** 국가를 가리지 않는 공통 규정 문서의 jurisdictions 값. */
    public static final String GLOBAL_CODE = "GLOBAL";

    /** 지원하는 국가의 모든 이름(별칭 포함) — {@link #resolveName(String)}의 조회표. */
    private static final Set<String> SUPPORTED_NAMES = Arrays.stream(values())
            .flatMap(jurisdiction -> jurisdiction.names.stream())
            .collect(Collectors.toUnmodifiableSet());

    /** 국가 텍스트를 낱말로 끊는 기준 — 공백과 쉼표. */
    private static final Pattern NAME_DELIMITER = Pattern.compile("[,\\s]+");

    /**
     * 그 나라 규정 문서가 쓰인 언어. 물품 이름을 이 말로 옮겨 검색 질의에 함께 싣는다
     * ({@link ssafy.checkmate.domain.checklist.service.ItemTranslationClient}).
     *
     * 언어 코드가 아니라 사람이 읽는 이름을 두는 이유는 그대로 번역 모델에게 건네기 때문이다 —
     * {@code zh-Hans}보다 "중국어 간체"가 덜 헷갈린다. 필리핀은 공식 항공보안 문서가 영어다.
     */
    private final String languageName;

    private final List<String> names;

    CountryJurisdiction(String languageName, String... names) {
        this.languageName = languageName;
        this.names = List.of(names);
    }

    public String getLanguageName() {
        return languageName;
    }

    /** 규정 문서가 한국어면 물품 이름을 그대로 검색하면 되므로 번역하지 않는다. */
    public boolean needsTranslation() {
        return this != KR;
    }

    public static Optional<CountryJurisdiction> from(String country) {
        if (country == null) {
            return Optional.empty();
        }
        String normalized = country.trim();
        for (CountryJurisdiction jurisdiction : values()) {
            if (jurisdiction.names.contains(normalized)) {
                return Optional.of(jurisdiction);
            }
        }
        return Optional.empty();
    }

    /**
     * 구글 자동완성이 준 국가 텍스트에서 지원 국가 이름 하나를 골라낸다.
     *
     * 후보의 국가 텍스트는 "일본"처럼 국가만 오기도 하고 "일본 도쿄도"처럼 상위 행정구역이
     * 붙어 오기도 해서, 통째로는 {@link #from(String)}에 걸리지 않는다. 그래서 낱말로 끊은 뒤
     * 지원 국가 이름과 맞는 첫 낱말을 국가로 확정한다. 맞는 낱말이 없으면 비어 있는 값을
     * 주고, 그 후보를 어떻게 할지는 부르는 쪽에 맡긴다.
     */
    public static Optional<String> resolveName(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return NAME_DELIMITER.splitAsStream(text.trim())
                .filter(SUPPORTED_NAMES::contains)
                .findFirst();
    }

    /** 검색 필터에 넣을 코드 목록 — 해당 국가와 공통 문서를 함께 본다. */
    public List<String> filterCodes() {
        return List.of(name(), GLOBAL_CODE);
    }
}
