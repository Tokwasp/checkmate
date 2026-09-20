package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;

/**
 * 인식한 물품 귀가 전 체크(33번)의 결과.
 *
 * 못 찾는 것도 정상 상태다 — 카메라가 2초마다 쏘는 화면이라 체크리스트에 없는 물건이
 * 자주 비친다. 그래서 실패가 아니라 matched=false로 알린다.
 */
@Getter
public class ReturnCheckScanResponse {

    private final boolean matched;
    /** 이 호출 이후 남은 미확인 물품 수. */
    private final int uncheckedCount;
    /** 방금 체크된 물품. 못 찾았으면 null. */
    private final ReturnCheckFoundItemResponse item;

    private ReturnCheckScanResponse(boolean matched, int uncheckedCount, ReturnCheckFoundItemResponse item) {
        this.matched = matched;
        this.uncheckedCount = uncheckedCount;
        this.item = item;
    }

    public static ReturnCheckScanResponse matched(int uncheckedCount, ReturnCheckFoundItemResponse item) {
        return new ReturnCheckScanResponse(true, uncheckedCount, item);
    }

    public static ReturnCheckScanResponse notMatched(int uncheckedCount) {
        return new ReturnCheckScanResponse(false, uncheckedCount, null);
    }
}
