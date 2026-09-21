package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 촬영 물품 반입 판정(35번) 멀티파트 요청. 사진 한 장뿐이다 — 저장하지 않으므로 여행·가방은 받지 않는다.
 * 형식·크기 검증은 {@code ScanImage}가 맡는다.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class QuarantineCheckRequest {

    @NotNull(message = "물체 이미지가 유효하지 않습니다.")
    private MultipartFile image;
}
