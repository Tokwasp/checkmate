package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 물품 인식 등록(26번) 멀티파트 요청.
 * 카테고리는 받지 않는다 — 서버가 itemClassId로 물품 마스터를 조회해 채운다.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ScanItemRequest {

    @NotNull(message = "물체 이미지가 유효하지 않습니다.")
    private MultipartFile image;

    @NotNull(message = "물품 ID는 필수입니다.")
    private Long itemClassId;

    @NotBlank(message = "물품 이름은 필수입니다.")
    private String name;

    @NotNull(message = "여행 ID는 필수입니다.")
    private Long tripId;

    @NotNull(message = "가방 ID는 필수입니다.")
    private Long bagId;

    @NotBlank(message = "유효하지 않은 규정 타입입니다.")
    private String regulationType;
}
