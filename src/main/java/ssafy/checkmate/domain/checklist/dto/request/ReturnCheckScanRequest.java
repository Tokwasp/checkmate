package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 귀가 전 점검(33번) — 카메라가 알아본 물품 이름.
 *
 * itemId가 아니라 이름을 받는 이유는 온디바이스 인식이 클래스 이름만 알려주기 때문이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnCheckScanRequest {

    @NotBlank(message = "물품 이름은 필수입니다.")
    private String name;
}
