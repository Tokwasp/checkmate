package ssafy.checkmate.domain.regulation.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 규정 판정. 응답에는 한글 라벨({@code 금지}·{@code 제한}·{@code 허용})로 나간다.
 *
 * 반입 규정 검사(30번)의 {@code 금지·주의·통과}와 이름이 다르다 — 그쪽은 물품마다 내리는
 * 검사 결과이고, 여기는 고정 안내라서 "가져갈 수 있는가"를 그대로 말하는 표현을 쓴다.
 */
public enum RegulationLevel {
    /** 그 조건에서는 가져갈 수 없다. */
    PROHIBITED("금지"),
    /** 수량·용량·범위 안에서만 가능하다. */
    RESTRICTED("제한"),
    /** 조건을 지키면 가져갈 수 있다. */
    ALLOWED("허용");

    private final String label;

    RegulationLevel(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
