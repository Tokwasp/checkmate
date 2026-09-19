package ssafy.checkmate.domain.checklist.service;

import lombok.Getter;

/**
 * 모델에게 물어볼 묶음 한 건. {@code (가방 이름, carriageType, 물품 이름)}이 같은 물품은
 * 판정이 어차피 같으므로 한 줄로 묶어 보내고, 받은 판정 하나를 묶음 전체에 복사한다.
 *
 * {@code carriageType}은 가방이 기내/위탁을 아직 정하지 않았으면 {@link #UNSPECIFIED}다 —
 * 가방은 생성 시 미지정으로 만들어지고 가방 수정(21번)으로만 값이 생긴다.
 */
@Getter
public class SecurityCheckTarget {

    public static final String UNSPECIFIED = "미지정";

    /** 묶음의 대표 물품 ID — 묶음 안에서 가장 작은 itemId. */
    private final Long itemId;
    private final String name;
    private final String bagName;
    private final String carriageType;
    /**
     * 그 나라 말로 된 검색 별칭. 규정 문서가 현지어라 한국어 이름만으로는 검색이 끊긴다 —
     * 질의에 이 값을 함께 실어 보낸다. 마스터에 없는 물품(체크리스트 직접 추가)은 빈 문자열.
     */
    private final String searchAlias;

    public SecurityCheckTarget(Long itemId, String name, String bagName, String carriageType) {
        this(itemId, name, bagName, carriageType, "");
    }

    public SecurityCheckTarget(Long itemId, String name, String bagName, String carriageType,
            String searchAlias) {
        this.itemId = itemId;
        this.name = name;
        this.bagName = bagName;
        this.carriageType = carriageType == null ? UNSPECIFIED : carriageType;
        this.searchAlias = searchAlias == null ? "" : searchAlias.trim();
    }

    /** 검색에 쓸 문구 — 한국어 이름 + 현지어 별칭. */
    public String searchTerms() {
        return searchAlias.isEmpty() ? name : name + " " + searchAlias;
    }
}
