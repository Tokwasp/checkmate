package ssafy.checkmate.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 즐겨찾기로 올릴 물건 하나 — id 가 아니라 이름이다.
 * 인벤토리 물건은 카메라가 찾아준 것이라 마스터에 아직 없을 수 있다. 이름으로 받아 있으면 별만 켜고
 * 없으면 새로 만든다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberFavoriteItemRequest {

    @NotBlank(message = "물건 이름이 누락되었습니다.")
    private String name;

    /** 인벤토리와 같은 분류 문자열(예: "서류"). 모르면 생략 */
    private String category;
}
