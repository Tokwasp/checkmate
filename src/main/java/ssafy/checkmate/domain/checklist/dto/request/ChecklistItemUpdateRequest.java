package ssafy.checkmate.domain.checklist.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 전달된 필드만 반영하는 부분 수정 요청. 전부 선택값이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItemUpdateRequest {

    private String name;
    private Integer quantity;
    private String category;
    private Boolean checked;
    private Long bagId;
}
