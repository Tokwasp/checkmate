package ssafy.checkmate.domain.checklist.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItemsCreateRequest {

    @NotEmpty(message = "추가할 항목이 없습니다.")
    @Valid
    private List<ChecklistItemCreateRequest> items;
}
