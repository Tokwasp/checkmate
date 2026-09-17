package ssafy.checkmate.domain.checklist.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Getter
public class ChecklistResponse {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int totalCount;
    private final int checkedCount;
    private final int uncheckedCount;
    private final List<WeatherResponse> weathers;
    private final List<ChecklistItemResponse> items;

    private ChecklistResponse(LocalDate startDate, LocalDate endDate, int totalCount, int checkedCount, int uncheckedCount,
                               List<WeatherResponse> weathers, List<ChecklistItemResponse> items) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalCount = totalCount;
        this.checkedCount = checkedCount;
        this.uncheckedCount = uncheckedCount;
        this.weathers = weathers;
        this.items = items;
    }

    public static ChecklistResponse of(LocalDate startDate, LocalDate endDate, int totalCount, int checkedCount, int uncheckedCount,
                                        List<WeatherResponse> weathers, List<ChecklistItemResponse> items) {
        return new ChecklistResponse(startDate, endDate, totalCount, checkedCount, uncheckedCount, weathers, items);
    }
}
