package ssafy.checkmate.domain.trip;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ssafy.checkmate.domain.trip.dto.request.TripCreateRequest;
import tools.jackson.databind.ObjectMapper;

final class TestRequestFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestRequestFactory() {
    }

    static TripCreateRequest tripCreateRequest(String placeId, String sessionToken,
                                                LocalDate startDate, LocalDate endDate, List<String> purposes) {
        return tripCreateRequest(placeId, sessionToken, startDate, endDate, purposes, false, null, null);
    }

    static TripCreateRequest tripCreateRequest(String placeId, String sessionToken,
                                                LocalDate startDate, LocalDate endDate, List<String> purposes,
                                                Boolean hasFlight, String departureAirline, String arrivalAirline) {
        Map<String, Object> map = new HashMap<>();
        map.put("placeId", placeId);
        map.put("sessionToken", sessionToken);
        map.put("startDate", startDate);
        map.put("endDate", endDate);
        map.put("purposes", purposes);
        map.put("hasFlight", hasFlight);
        map.put("departureAirline", departureAirline);
        map.put("arrivalAirline", arrivalAirline);
        return MAPPER.convertValue(map, TripCreateRequest.class);
    }
}
