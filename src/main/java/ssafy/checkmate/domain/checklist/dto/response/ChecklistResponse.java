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
    private final ChecklistWeatherResponse weather;
    private final List<ChecklistBagResponse> bags;
    private final List<ChecklistItemSummaryResponse> items;

    private ChecklistResponse(LocalDate startDate, LocalDate endDate, int totalCount, int checkedCount, int uncheckedCount,
                               ChecklistWeatherResponse weather, List<ChecklistBagResponse> bags,
                               List<ChecklistItemSummaryResponse> items) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalCount = totalCount;
        this.checkedCount = checkedCount;
        this.uncheckedCount = uncheckedCount;
        this.weather = weather;
        this.bags = bags;
        this.items = items;
    }

    public static ChecklistResponse of(LocalDate startDate, LocalDate endDate, int totalCount, int checkedCount, int uncheckedCount,
                                        ChecklistWeatherResponse weather, List<ChecklistBagResponse> bags,
                                        List<ChecklistItemSummaryResponse> items) {
        return new ChecklistResponse(startDate, endDate, totalCount, checkedCount, uncheckedCount, weather, bags, items);
    }
}
