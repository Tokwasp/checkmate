package ssafy.checkmate.domain.checklist.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

@Getter
public class ChecklistResponse {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<WeatherResponse> weathers;
    private final List<ChecklistItemResponse> items;

    private ChecklistResponse(LocalDate startDate, LocalDate endDate,
                               List<WeatherResponse> weathers, List<ChecklistItemResponse> items) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.weathers = weathers;
        this.items = items;
    }

    public static ChecklistResponse of(LocalDate startDate, LocalDate endDate,
                                        List<WeatherResponse> weathers, List<ChecklistItemResponse> items) {
        return new ChecklistResponse(startDate, endDate, weathers, items);
    }
}
