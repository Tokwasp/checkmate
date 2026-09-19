package ssafy.checkmate.domain.checklist.entity;

import java.util.List;
import java.util.Optional;

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

    KR("ko", "대한민국", "한국"),
    JP("ja", "일본"),
    CN("zh-Hans", "중국"),
    TW("zh-Hant", "대만"),
    TH("th", "태국"),
    VN("vi", "베트남"),
    PH("en", "필리핀"),
    US("en", "미국", "미합중국"),
    GB("en", "영국"),
    FR("fr", "프랑스"),
    IT("it", "이탈리아");

    /** 국가를 가리지 않는 공통 규정 문서의 jurisdictions 값. */
    public static final String GLOBAL_CODE = "GLOBAL";

    /**
     * 그 나라 규정 문서가 쓰인 언어. 검색 별칭을 고를 때 쓴다
     * ({@link ItemClass#aliasFor(String)}).
     *
     * 필리핀은 공식 항공보안 문서가 영어라 {@code en}을 쓴다. 한국은 질의 자체가 한국어라
     * 별칭이 필요 없지만, 코드 분기를 없애려고 {@code ko}를 두고 별칭을 비워 둔다.
     */
    private final String language;

    private final List<String> names;

    CountryJurisdiction(String language, String... names) {
        this.language = language;
        this.names = List.of(names);
    }

    public String getLanguage() {
        return language;
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

    /** 검색 필터에 넣을 코드 목록 — 해당 국가와 공통 문서를 함께 본다. */
    public List<String> filterCodes() {
        return List.of(name(), GLOBAL_CODE);
    }
}
