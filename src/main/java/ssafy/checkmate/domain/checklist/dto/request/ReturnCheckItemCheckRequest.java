package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 귀가 전 점검 단건 체크·해제(33-2번) — 손으로 켜고 끄는 값.
 *
 * 토글이 아니라 <b>바꿀 상태를 그대로 받는다</b>. 카메라(33번)와 손이 같은 줄을 동시에 건드릴 수
 * 있어서, "뒤집어라"로 보내면 화면이 보던 상태와 서버 상태가 어긋났을 때 반대로 뒤집힌다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnCheckItemCheckRequest {

    @NotNull(message = "체크 여부는 필수입니다.")
    private Boolean returnChecked;
}
