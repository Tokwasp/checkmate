package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;

/**
 * 여행 국가명을 지식베이스 필터 코드로 바꾸는 매핑표 테스트.
 * 코드 값은 S3 버킷 {@code checkmate-regulation-docs}의 국가 프리픽스와 같아야 한다.
 */
class CountryJurisdictionTest {

    @ParameterizedTest
    @CsvSource({
            "대한민국, KR",
            "한국, KR",
            "일본, JP",
            "중국, CN",
            "대만, TW",
            "태국, TH",
            "베트남, VN",
            "필리핀, PH",
            "미국, US",
            "영국, GB",
            "프랑스, FR",
            "이탈리아, IT"
    })
    @DisplayName("코퍼스가 다루는 국가명은 지식베이스 코드로 바뀐다")
    void mapsSupportedCountries(String country, String expectedCode) {
        assertThat(CountryJurisdiction.from(country))
                .isPresent()
                .get()
                .extracting(Enum::name)
                .isEqualTo(expectedCode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"브라질", "독일", "호주", "스페인"})
    @DisplayName("코퍼스가 다루지 않는 국가는 매핑되지 않는다")
    void doesNotMapUnsupportedCountries(String country) {
        assertThat(CountryJurisdiction.from(country)).isEmpty();
    }

    @Test
    @DisplayName("국가명이 null이거나 앞뒤 공백이 있어도 안전하게 처리한다")
    void handlesNullAndWhitespace() {
        assertThat(CountryJurisdiction.from(null)).isEmpty();
        assertThat(CountryJurisdiction.from("  일본  ")).contains(CountryJurisdiction.JP);
    }

    @ParameterizedTest
    @CsvSource({
            "일본, 일본",
            "'일본 도쿄도', 일본",
            "'일본 도쿄도 시부야구', 일본",
            "'도쿄도, 일본', 일본",
            "'  일본  ', 일본",
            "'미국 캘리포니아주', 미국",
            "'대한민국 경기도', 대한민국"
    })
    @DisplayName("국가 텍스트에 상위 행정구역이 섞여 있어도 지원 국가 이름만 뽑아낸다")
    void resolvesSupportedCountryName(String text, String expected) {
        assertThat(CountryJurisdiction.resolveName(text)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"독일 바이에른주", "브라질", "미국령 사모아"})
    @DisplayName("지원 국가 이름이 하나도 없으면 아무것도 뽑지 않는다")
    void resolvesNothingForUnsupportedText(String text) {
        assertThat(CountryJurisdiction.resolveName(text)).isEmpty();
    }

    @Test
    @DisplayName("국가 텍스트가 null이어도 안전하게 처리한다")
    void resolveNameHandlesNull() {
        assertThat(CountryJurisdiction.resolveName(null)).isEmpty();
    }

    @Test
    @DisplayName("뽑아낸 국가 이름은 곧바로 관할 코드로 이어진다")
    void resolvedNameMapsToJurisdiction() {
        String country = CountryJurisdiction.resolveName("일본 도쿄도").orElseThrow();

        assertThat(CountryJurisdiction.from(country)).contains(CountryJurisdiction.JP);
    }

    @Test
    @DisplayName("검색 필터에는 국가 코드와 공통 문서 코드(GLOBAL)를 함께 넣는다")
    void filterCodesIncludeGlobal() {
        assertThat(CountryJurisdiction.JP.filterCodes())
                .containsExactly("JP", CountryJurisdiction.GLOBAL_CODE);
    }
}
