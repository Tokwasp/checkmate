package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가방 물품 수정(27번) 요청. quantity·checked는 전달된 것만 반영하는 부분 수정이고,
 * bagId·name은 화면이 보고 있던 대상이 맞는지 확인하는 용도다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagItemUpdateRequest {

    @NotNull(message = "가방 ID는 필수입니다.")
    private Long bagId;

    @NotBlank(message = "물품 이름은 필수입니다.")
    private String name;

    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private Integer quantity;

    private Boolean checked;
}
