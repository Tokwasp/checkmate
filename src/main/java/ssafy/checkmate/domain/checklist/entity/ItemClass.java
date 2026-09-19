package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * 물품 마스터. 온디바이스 모델이 인식할 수 있는 클래스(AI/classes.txt의 23종)마다
 * 카테고리를 미리 저장해 둔 기준 데이터다.
 *
 * ID를 자동 생성하지 않고 직접 넣는다 — 앱이 보내는 클래스 ID와 값이 같아야 하기 때문이다.
 * 초기 데이터는 {@link ssafy.checkmate.domain.checklist.service.ItemClassSeeder}가 넣는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemClass extends BaseEntity {

    @Id
    private Long id;

    private String className;

    private String category;

    /**
     * 반입 규정 검사(30번)의 <b>검색 별칭</b>. 언어 코드({@link CountryJurisdiction#getLanguage()})
     * → 그 언어의 검색어다.
     *
     * 규정 문서는 각 나라 현지어로 쓰여 있는데 우리 질의는 한국어라, 임베딩이 언어를 건너
     * 매칭하지 못해 규정을 놓친다(교차언어 검색 실패). 예를 들어 "가위"로는 일본어 코퍼스의
     * `はさみ` 규정이 검색 20위 안에 아예 안 잡히는데, 질의에 `はさみ`를 같이 실으면 2위로
     * 올라온다. 그래서 물품마다 현지어를 미리 적어 두고 질의에 붙인다.
     *
     * 값에는 직역뿐 아니라 <b>규정에서 쓰는 분류 용어</b>도 함께 넣는다(칼 → `刃物 凶器`,
     * 치약 → `液体物`). 규정 문서는 개별 물품명보다 분류명으로 서술되는 경우가 많다.
     */
    @Convert(converter = SearchAliasJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> searchAliases = new LinkedHashMap<>();

    private ItemClass(Long id, String className, String category, Map<String, String> searchAliases) {
        this.id = id;
        this.className = className;
        this.category = category;
        this.searchAliases = searchAliases != null ? searchAliases : new LinkedHashMap<>();
    }

    public static ItemClass of(Long id, String className, String category) {
        return new ItemClass(id, className, category, new LinkedHashMap<>());
    }

    public static ItemClass of(Long id, String className, String category, Map<String, String> searchAliases) {
        return new ItemClass(id, className, category, searchAliases);
    }

    /** 그 언어의 별칭. 없으면 빈 문자열 — 질의에 아무것도 덧붙이지 않는다는 뜻이다. */
    public String aliasFor(String language) {
        if (language == null) {
            return "";
        }
        return searchAliases.getOrDefault(language, "");
    }
}
