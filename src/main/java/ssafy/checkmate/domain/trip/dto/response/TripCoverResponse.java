package ssafy.checkmate.domain.trip.dto.response;

import ssafy.checkmate.domain.trip.entity.Trip;

/** 여행 표지 사진 교체·삭제의 응답 — 바뀐 URL 만 돌려준다. 지우면 null. */
public record TripCoverResponse(Long tripId, String coverImageUrl) {

    public static TripCoverResponse of(Trip trip) {
        return new TripCoverResponse(trip.getId(), trip.getCoverImageUrl());
    }
}
