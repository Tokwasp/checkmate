package ssafy.checkmate.domain.checklist.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.trip.entity.Trip;

/**
 * 귀가 전 점검(31번) 화면 한 장이 필요한 전부 — 여행 머리글·진행률·가방별 물품 목록.
 *
 * 최상위 합계는 {@code bags[0]}('전체')의 값과 같다. 진행바가 bags 배열을 뒤지지 않고
 * 값 하나만 읽게 하려고 중복해서 둔다.
 */
@Getter
public class ReturnCheckResponse {

    private final Long tripId;
    private final String country;
    private final String region;
    private final LocalDate startDate;
    private final LocalDate endDate;
    /** 귀국(입국) 항공사. 항공편 없는 여행은 null. */
    private final String arrivalAirline;
    private final int totalCount;
    private final int returnCheckedCount;
    private final List<ReturnCheckBagResponse> bags;

    private ReturnCheckResponse(Long tripId, String country, String region, LocalDate startDate, LocalDate endDate,
            String arrivalAirline, int totalCount, int returnCheckedCount, List<ReturnCheckBagResponse> bags) {
        this.tripId = tripId;
        this.country = country;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
        this.arrivalAirline = arrivalAirline;
        this.totalCount = totalCount;
        this.returnCheckedCount = returnCheckedCount;
        this.bags = bags;
    }

    /** bags의 첫 원소는 항상 합산 가방 '전체'이고, 최상위 합계는 그 값을 그대로 쓴다. */
    public static ReturnCheckResponse of(Trip trip, List<ReturnCheckBagResponse> bags) {
        ReturnCheckBagResponse all = bags.get(0);
        return new ReturnCheckResponse(
                trip.getId(), trip.getCountry(), trip.getRegion(), trip.getStartDate(), trip.getEndDate(),
                trip.getArrivalAirline(), all.getTotalCount(), all.getReturnCheckedCount(), bags);
    }
}
