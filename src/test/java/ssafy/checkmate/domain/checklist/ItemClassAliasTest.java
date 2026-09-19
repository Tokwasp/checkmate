package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.CountryJurisdiction;
import ssafy.checkmate.domain.checklist.entity.ItemClass;
import ssafy.checkmate.domain.checklist.repository.ItemClassRepository;
import ssafy.checkmate.domain.checklist.service.SecurityCheckTarget;

/**
 * 반입 규정 검사(30번)의 검색 별칭이 물품 마스터에 제대로 들어갔는지 확인한다.
 *
 * 별칭이 비어 있으면 그 나라 규정을 검색으로 못 찾아 <b>금지 물품이 조용히 통과로 판정된다</b>.
 * 값이 틀렸는지까지는 여기서 못 잡지만, 빠진 건 여기서 잡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ItemClassAliasTest {

    /** 규정 문서가 한국어가 아니라 별칭이 반드시 필요한 나라들. */
    private static final List<CountryJurisdiction> FOREIGN_LANGUAGE_COUNTRIES = List.of(
            CountryJurisdiction.JP, CountryJurisdiction.CN, CountryJurisdiction.TW,
            CountryJurisdiction.TH, CountryJurisdiction.VN, CountryJurisdiction.US,
            CountryJurisdiction.GB, CountryJurisdiction.PH, CountryJurisdiction.FR,
            CountryJurisdiction.IT);

    @Autowired
    private ItemClassRepository itemClassRepository;

    @ParameterizedTest
    @EnumSource(CountryJurisdiction.class)
    @DisplayName("한국을 뺀 모든 국가에서 23종 전부에 검색 별칭이 있다")
    void everyItemHasAliasForEveryForeignCountry(CountryJurisdiction jurisdiction) {
        List<ItemClass> itemClasses = itemClassRepository.findAll();
        assertThat(itemClasses).hasSize(23);

        List<String> missing = itemClasses.stream()
                .filter(itemClass -> itemClass.aliasFor(jurisdiction.getLanguage()).isBlank())
                .map(ItemClass::getClassName)
                .toList();

        if (jurisdiction == CountryJurisdiction.KR) {
            assertThat(missing).hasSize(23);  // 질의가 이미 한국어라 별칭이 필요 없다
            return;
        }
        assertThat(missing).as("%s(%s) 별칭 누락", jurisdiction, jurisdiction.getLanguage()).isEmpty();
    }

    @Test
    @DisplayName("규정 문서가 외국어인 나라는 모두 언어 코드를 갖는다")
    void foreignCountriesHaveLanguageCode() {
        assertThat(FOREIGN_LANGUAGE_COUNTRIES)
                .allSatisfy(country -> assertThat(country.getLanguage()).isNotBlank());
        assertThat(CountryJurisdiction.JP.getLanguage()).isEqualTo("ja");
        assertThat(CountryJurisdiction.CN.getLanguage()).isEqualTo("zh-Hans");
        assertThat(CountryJurisdiction.TW.getLanguage()).isEqualTo("zh-Hant");
        assertThat(CountryJurisdiction.PH.getLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("위험 물품 별칭에는 직역뿐 아니라 규정이 쓰는 분류 용어도 들어 있다")
    void riskyItemsIncludeRegulatoryTerms() {
        assertThat(aliasOf("칼", "ja")).contains("刃物");
        assertThat(aliasOf("가위", "ja")).contains("はさみ");
        assertThat(aliasOf("치약", "ja")).contains("液体物");
        assertThat(aliasOf("보조배터리", "ja")).contains("リチウムイオン電池");
        assertThat(aliasOf("칼", "zh-Hant")).contains("刀具");
        assertThat(aliasOf("보조배터리", "zh-Hant")).contains("行動電源");
    }

    @Test
    @DisplayName("검색어는 한국어 이름 뒤에 현지어 별칭을 붙인 형태다")
    void searchTermsCombineNameAndAlias() {
        SecurityCheckTarget withAlias =
                new SecurityCheckTarget(1L, "칼", "기내백", "기내", "刃物 ナイフ 凶器");
        assertThat(withAlias.searchTerms()).isEqualTo("칼 刃物 ナイフ 凶器");
    }

    @Test
    @DisplayName("마스터에 없는 물품은 별칭 없이 한국어 이름만으로 검색한다")
    void searchTermsFallBackToNameOnly() {
        SecurityCheckTarget noAlias = new SecurityCheckTarget(1L, "직접 넣은 물품", "기내백", "기내");
        assertThat(noAlias.searchTerms()).isEqualTo("직접 넣은 물품");
    }

    private String aliasOf(String className, String language) {
        return itemClassRepository.findByClassName(className).orElseThrow().aliasFor(language);
    }
}
