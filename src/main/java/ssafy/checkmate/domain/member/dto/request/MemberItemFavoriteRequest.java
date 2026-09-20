package ssafy.checkmate.domain.member.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 즐겨찾기 켜기/끄기 — 본문 {@code { "isFavorite": true }}. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberItemFavoriteRequest {

    // Boolean(래퍼)로 둔다 — 기본형 boolean 이면 Lombok 게터가 isFavorite() 이 되어 JSON 이름이 "favorite" 으로 어긋난다
    @NotNull(message = "즐겨찾기 값이 누락되었습니다.")
    private Boolean isFavorite;
}
