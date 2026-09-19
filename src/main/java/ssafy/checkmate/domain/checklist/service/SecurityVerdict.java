package ssafy.checkmate.domain.checklist.service;

import lombok.Getter;

/**
 * 모델이 돌려준 판정 한 건. {@code itemId}는 질의할 때 보낸 묶음의 대표 물품 ID이며,
 * 서비스는 이 값으로 묶음을 찾아 그 묶음의 모든 물품에 같은 판정을 적용한다.
 *
 * 이름이 아니라 ID로 되돌려 붙이는 이유는, 같은 이름 물품이 여러 가방에 따로 존재할 수
 * 있어서(26번 참고) 이름으로 맞추면 엉뚱한 행에 판정이 붙기 때문이다.
 */
@Getter
public class SecurityVerdict {

    private final Long itemId;
    private final VerdictLevel level;
    /** 통과 물품은 null. */
    private final String guide;

    public SecurityVerdict(Long itemId, VerdictLevel level, String guide) {
        this.itemId = itemId;
        this.level = level;
        this.guide = level == VerdictLevel.NONE ? null : guide;
    }
}
