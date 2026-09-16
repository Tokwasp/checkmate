package ssafy.checkmate.domain.trip.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 여행 생성(6번)/수정(8번) 공용 요청. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripCreateRequest {

    /** 자동완성 후보에서 선택한 국가/지역 — 프론트가 그대로 담아 보낸다. */
    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String country;

    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String region;

    /** 목적지 상세 조회(6-0-1번)에서 받아 프론트가 기억해둔 좌표 — 여기서는 그대로 저장만 한다. */
    @NotNull(message = "필수 여행 정보가 누락되었습니다.")
    private Double latitude;

    @NotNull(message = "필수 여행 정보가 누락되었습니다.")
    private Double longitude;

    /** 자동완성 후보에서 선택한 국가/지역 — Place Details엔 없는 정보라 프론트가 그대로 담아 보낸다. */
    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String country;

    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String region;

    @NotNull(message = "필수 여행 정보가 누락되었습니다.")
    private LocalDate startDate;

    @NotNull(message = "필수 여행 정보가 누락되었습니다.")
    private LocalDate endDate;

    /** 널 가능 — 여행 목적을 하나도 선택하지 않을 수 있다. 값이 있으면 원소는 공백 불가 */
    private List<@NotBlank String> purposes;

    @NotNull(message = "필수 여행 정보가 누락되었습니다.")
    private Boolean hasFlight;

    private String departureAirline;

    private String arrivalAirline;
}
