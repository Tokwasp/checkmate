package ssafy.checkmate.domain.trip.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 여행 목적(캠핑, 수영 등) 여러 개를 가질 수 있다 — 체크리스트 추천이 이 값을 참고한다. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> purposes = new ArrayList<>();

    private boolean hasFlight;

    private String departureAirline;

    private String arrivalAirline;

    /** Google Places Details로 확인한 목적지 좌표. 날씨 조회 시 지오코딩 없이 바로 쓴다. */
    private Double latitude;

    private Double longitude;

    @Builder
    public Trip(Long memberId, String destination, LocalDate startDate, LocalDate endDate,
                List<String> purposes, boolean hasFlight, String departureAirline, String arrivalAirline,
                Double latitude, Double longitude) {
        this.memberId = memberId;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.purposes = purposes != null ? purposes : new ArrayList<>();
        this.hasFlight = hasFlight;
        this.departureAirline = hasFlight ? departureAirline : null;
        this.arrivalAirline = hasFlight ? arrivalAirline : null;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    /** PUT 수정 — 전달된 값으로 전체 교체(부분 수정 아님, 여행 생성과 동일한 요청 형식). */
    public void update(String destination, Double latitude, Double longitude,
                        LocalDate startDate, LocalDate endDate, List<String> purposes,
                        boolean hasFlight, String departureAirline, String arrivalAirline) {
        this.destination = destination;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startDate = startDate;
        this.endDate = endDate;
        this.purposes = purposes != null ? purposes : new ArrayList<>();
        this.hasFlight = hasFlight;
        this.departureAirline = hasFlight ? departureAirline : null;
        this.arrivalAirline = hasFlight ? arrivalAirline : null;
    }

    public TripType getType(LocalDate today) {
        return endDate.isBefore(today) ? TripType.PAST : TripType.CURRENT;
    }
}
