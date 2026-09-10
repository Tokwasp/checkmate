package ssafy.checkmate.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개발용 로그인 요청. 이메일만 받아 해당 회원으로 토큰을 발급한다.
 * 기본 생성자를 protected로 두는 이유: JSON 역직렬화(Jackson)에는 필요하지만 코드에서 new로 만드는 건 막기 위해.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevLoginRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
