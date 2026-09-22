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
     * 검색에 쓸 문구 — 생성자가 준 값 그대로다({@link #searchTerms()}는 가공하지 않는다).
     * 빈 문자열이면 그 물품은 검색 자체를 건너뛴다({@link BedrockAviationRegulationClient}).
     */
    private final String localTerms;

    /**
     * 현지어를 안 넘기는 간단한 생성자 — <b>이름 자체를 검색어로 쓴다.</b> 운영 코드
     * ({@link SecurityCheckService})는 이 생성자를 쓰지 않는다 — 한국행이면 이름을, 번역이
     * 필요한 나라면 GMS 결과(성공했으면 그 값, 실패했으면 <b>빈 문자열</b>)를 5-인자
     * 생성자에 명시로 넘긴다. 번역 실패를 이름으로 대체하지 않는 이유는 {@link #searchTerms()}
     * 참고. 이 4-인자 생성자는 테스트에서 번역을 신경 쓰지 않고 간단히 만들 때만 쓴다.
     */
    public SecurityCheckTarget(Long itemId, String name, String bagName, String carriageType) {
        this(itemId, name, bagName, carriageType, name);
    }

    public SecurityCheckTarget(Long itemId, String name, String bagName, String carriageType,
            String localTerms) {
        this.itemId = itemId;
        this.name = name;
        this.bagName = bagName;
        this.carriageType = carriageType == null ? UNSPECIFIED : carriageType;
        this.localTerms = localTerms == null ? "" : localTerms.trim();
    }

    /**
     * 검색에 쓸 문구 — <b>생성자가 준 {@code localTerms}를 그대로 돌려준다.</b> 예전엔
     * 비어 있으면 한국어 이름으로 대체했는데(한국어+현지어 안전망), 실측
     * (RAG_검색품질_모델선정_리포트.md §7·§9-5)으로 그렇게 대체해서 검색해도 결과가 안
     * 좋다는 게 확인됐다 — JP·TW 일부 물품은 한국어 질의로 정답 청크를 top-15 안에서 아예
     * 못 찾았다. 그래서 <b>대체하지 않는다</b> — 빈 문자열이면 그 물품은 검색을 건너뛰고
     * "근거 없음 → NONE" 규칙대로 판정된다(§11-5). 무엇을 넣을지는 호출하는 쪽
     * ({@link SecurityCheckService#searchTermFor})이 결정한다.
     */
    public String searchTerms() {
        return localTerms;
    }
}
