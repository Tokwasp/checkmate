package ssafy.checkmate.domain.member.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인벤토리에서 고른 물건 여러 개를 한 번에 즐겨찾기로 — 본문 {@code { "items": [ { "name", "category"? } ] }}. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberFavoritesRequest {

    @NotEmpty(message = "추가할 물건이 없습니다.")
    @Valid
    private List<MemberFavoriteItemRequest> items;
}
