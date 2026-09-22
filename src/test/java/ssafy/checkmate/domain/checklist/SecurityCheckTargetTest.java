package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;

/**
 * 반입 규정 검사(30번)가 지식베이스에 보내는 검색어 조립을 확인한다.
 *
 * 현지어가 빠지면 그 나라 규정을 검색으로 못 찾아 <b>금지 물품이 조용히 통과로 판정된다</b>.
 */
class SecurityCheckTargetTest {

    @Test
    @DisplayName("검색어는 한국어 이름 뒤에 현지어 검색어를 붙인 형태다")
    void searchTermsCombineNameAndLocalTerms() {
        SecurityCheckTarget target =
                new SecurityCheckTarget(1L, "칼", "기내백", "기내", "刃物 ナイフ 凶器");

        assertThat(target.searchTerms()).isEqualTo("칼 刃物 ナイフ 凶器");
    }

    @Test
    @DisplayName("번역을 못 받은 물품은 한국어 이름만으로 검색한다")
    void searchTermsFallBackToNameOnly() {
        SecurityCheckTarget target = new SecurityCheckTarget(1L, "직접 넣은 물품", "기내백", "기내");

        assertThat(target.searchTerms()).isEqualTo("직접 넣은 물품");
    }

    @Test
    @DisplayName("규정 문서가 한국어인 한국행만 번역이 필요 없다")
    void onlyKoreaSkipsTranslation() {
        assertThat(CountryJurisdiction.KR.needsTranslation()).isFalse();
        assertThat(CountryJurisdiction.JP.needsTranslation()).isTrue();
        assertThat(CountryJurisdiction.PH.needsTranslation()).isTrue();
    }

    @Test
    @DisplayName("번역 모델에게 건넬 언어 이름은 간체·번체까지 구분한다")
    void languageNamesDistinguishChineseVariants() {
        assertThat(CountryJurisdiction.CN.getLanguageName()).isEqualTo("중국어 간체");
        assertThat(CountryJurisdiction.TW.getLanguageName()).isEqualTo("중국어 번체");
        assertThat(CountryJurisdiction.PH.getLanguageName()).isEqualTo("영어");
        assertThat(CountryJurisdiction.JP.getLanguageName()).isEqualTo("일본어");
    }
}
