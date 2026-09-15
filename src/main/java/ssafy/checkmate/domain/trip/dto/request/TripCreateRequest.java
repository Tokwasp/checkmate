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

    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String placeId;

    @NotBlank(message = "필수 여행 정보가 누락되었습니다.")
    private String sessionToken;

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
