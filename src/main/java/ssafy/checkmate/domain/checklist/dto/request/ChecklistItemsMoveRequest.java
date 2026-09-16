package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItemsMoveRequest {

    @NotEmpty(message = "옮길 항목이 없습니다.")
    private List<Long> itemIds;

    @NotNull(message = "가방 ID가 누락되었습니다.")
    private Long bagId;
}
