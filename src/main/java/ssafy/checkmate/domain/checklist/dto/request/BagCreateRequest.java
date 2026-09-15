package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagCreateRequest {

    @NotBlank(message = "가방 이름이 누락되었습니다.")
    private String name;
}
