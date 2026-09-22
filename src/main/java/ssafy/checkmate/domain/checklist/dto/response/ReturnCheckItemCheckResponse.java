package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;

/**
 * 귀가 전 점검 단건 체크·해제(33-2번)의 결과.
 *
 * 33번(카메라 체크)과 <b>같은 모양</b>이다 — 두 동작이 남기는 값이 같은 컬럼 하나
 * (return_checked)라, 화면도 같은 코드로 "안 보임 N" 배지와 그 줄만 갈아 끼우면 된다.
 * 다만 이쪽은 itemId로 직접 찾으므로 "못 찾음"이 없어 matched를 내리지 않는다.
 */
@Getter
public class ReturnCheckItemCheckResponse {

    /** 이 호출 이후 남은 미확인 물품 수. */
    private final int uncheckedCount;
    /** 방금 바뀐 물품. 해제한 경우 returnChecked가 false로 온다. */
    private final ReturnCheckFoundItemResponse item;

    private ReturnCheckItemCheckResponse(int uncheckedCount, ReturnCheckFoundItemResponse item) {
        this.uncheckedCount = uncheckedCount;
        this.item = item;
    }

    public static ReturnCheckItemCheckResponse of(int uncheckedCount, ReturnCheckFoundItemResponse item) {
        return new ReturnCheckItemCheckResponse(uncheckedCount, item);
    }
}
