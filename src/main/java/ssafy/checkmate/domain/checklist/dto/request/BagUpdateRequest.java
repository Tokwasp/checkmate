package ssafy.checkmate.domain.checklist.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagUpdateRequest {

    private String name;
    private String carriageType;
}
