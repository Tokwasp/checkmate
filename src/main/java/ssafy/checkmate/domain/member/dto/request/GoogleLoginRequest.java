package ssafy.checkmate.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleLoginRequest {

    @NotBlank(message = "구글 ID 토큰은 필수입니다.")
    private String idToken;
}
