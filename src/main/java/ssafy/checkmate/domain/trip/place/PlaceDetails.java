package ssafy.checkmate.domain.trip.place;

import lombok.Getter;

/** 사용자가 고른 장소의 상세 정보 — 여행 저장에 필요한 만큼만 담는다. */
@Getter
public class PlaceDetails {

    private final Double latitude;
    private final Double longitude;

    public PlaceDetails(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
