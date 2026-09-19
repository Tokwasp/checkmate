package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;

@Getter
public class SecurityCheckResponse {

    private final int violationCount;
    private final int warningCount;
    private final int passCount;
    private final List<SecurityCheckItemResponse> items;

    private SecurityCheckResponse(int violationCount, int warningCount, int passCount,
            List<SecurityCheckItemResponse> items) {
        this.violationCount = violationCount;
        this.warningCount = warningCount;
        this.passCount = passCount;
        this.items = items;
    }

    public static SecurityCheckResponse of(int violationCount, int warningCount, int passCount,
            List<SecurityCheckItemResponse> items) {
        return new SecurityCheckResponse(violationCount, warningCount, passCount, items);
    }
}
