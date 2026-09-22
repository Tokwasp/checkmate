package ssafy.checkmate.domain.checklist.exception;

import ssafy.checkmate.global.exception.BusinessException;

/**
 * 짐 쌀 때 담지 않은(departureChecked=false) 물품을 귀가 전 점검에서 체크하려 한 경우.
 *
 * 물품 자체는 있으니 404가 아니다. 안 담은 물건을 "도로 챙겼다"로 만들면 31·32번 목록에는
 * 보이지 않는데 개수만 어긋난다.
 */
public class NotReturnCheckTargetException extends BusinessException {

    public NotReturnCheckTargetException() {
        super(BagItemErrorCode.NOT_RETURN_CHECK_TARGET);
    }
}
