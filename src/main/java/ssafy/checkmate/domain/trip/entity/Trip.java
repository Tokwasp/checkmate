package ssafy.checkmate.domain.trip.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    private String country;

    private String region;

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

    /**
     * 여행 준비 진행 단계. "여행 정보 입력" 단계는 여행 생성 요청 자체로 완료되므로,
     * 생성 시 항상 {@link TripStep#CHECKLIST_CREATION}에서 시작한다.
     */
    @Enumerated(EnumType.STRING)
    private TripStep currentStep;

    @Builder
    public Trip(Long memberId, String country, String region, LocalDate startDate, LocalDate endDate,
                List<String> purposes, boolean hasFlight, String departureAirline, String arrivalAirline,
                Double latitude, Double longitude) {
        this.memberId = memberId;
        this.country = country;
        this.region = region;
        this.startDate = startDate;
        this.endDate = endDate;
        this.purposes = purposes != null ? purposes : new ArrayList<>();
        this.hasFlight = hasFlight;
        this.departureAirline = hasFlight ? departureAirline : null;
        this.arrivalAirline = hasFlight ? arrivalAirline : null;
        this.latitude = latitude;
        this.longitude = longitude;
        this.currentStep = TripStep.CHECKLIST_CREATION;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    /** PUT 수정 — 전달된 값으로 전체 교체(부분 수정 아님, 여행 생성과 동일한 요청 형식). */
    public void update(String country, String region, Double latitude, Double longitude,
                        LocalDate startDate, LocalDate endDate, List<String> purposes,
                        boolean hasFlight, String departureAirline, String arrivalAirline) {
        this.country = country;
        this.region = region;
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
        if (endDate.isBefore(today)) {
            return TripType.PAST;
        }
        return startDate.isAfter(today) ? TripType.UPCOMING : TripType.ONGOING;
    }

    /** 이미 지난 단계로는 되돌리지 않고, 더 앞선 단계일 때만 갱신한다. */
    public void advanceStepAtLeast(TripStep step) {
        if (currentStep.getOrder() < step.getOrder()) {
            this.currentStep = step;
        }
    }
}
