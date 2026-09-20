package ssafy.checkmate.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.trip.dto.response.TripCoverResponse;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.InvalidCoverImageException;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.domain.trip.service.TripCoverService;
import ssafy.checkmate.global.storage.ImageStorage;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripCoverServiceTest {

    @Autowired
    private TripCoverService tripCoverService;

    @Autowired
    private TripRepository tripRepository;

    /** 실제 저장소(로컬 파일)에 쓰지 않도록 흉내 낸다 — 어디에 어떤 폴더로 올리는지만 본다 */
    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("표지를 올리면 회원/여행 폴더의 cover 아래 저장하고 coverImageUrl 을 바꾼다")
    void updateCover() {
        Trip trip = tripRepository.save(trip(1L));
        String expectedUrl = "/uploads/1/trips/" + trip.getId() + "/cover/abc.png";
        given(imageStorage.upload(any(), eq("1/trips/" + trip.getId() + "/cover"))).willReturn(expectedUrl);

        TripCoverResponse response = tripCoverService.updateCover(trip.getId(), 1L, png());

        assertThat(response.tripId()).isEqualTo(trip.getId());
        assertThat(response.coverImageUrl()).isEqualTo(expectedUrl);
        assertThat(tripRepository.findById(trip.getId()).orElseThrow().getCoverImageUrl()).isEqualTo(expectedUrl);
    }

    @Test
    @DisplayName("비어 있거나 이미지가 아닌 파일은 거절하고 저장소를 건드리지 않는다")
    void updateCoverRejectsInvalidImage() {
        Trip trip = tripRepository.save(trip(1L));

        assertThatThrownBy(() -> tripCoverService.updateCover(trip.getId(), 1L, null))
                .isInstanceOf(InvalidCoverImageException.class);
        assertThatThrownBy(() -> tripCoverService.updateCover(trip.getId(), 1L,
                new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidCoverImageException.class);
        assertThatThrownBy(() -> tripCoverService.updateCover(trip.getId(), 1L,
                new MockMultipartFile("image", "note.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(InvalidCoverImageException.class);

        then(imageStorage).should(never()).upload(any(), any());
        assertThat(tripRepository.findById(trip.getId()).orElseThrow().getCoverImageUrl()).isNull();
    }

    @Test
    @DisplayName("없는 여행이면 404, 남의 여행이면 403 이고 저장소를 건드리지 않는다")
    void updateCoverRejectsWrongTrip() {
        Trip others = tripRepository.save(trip(2L));

        assertThatThrownBy(() -> tripCoverService.updateCover(999_999L, 1L, png()))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> tripCoverService.updateCover(others.getId(), 1L, png()))
                .isInstanceOf(TripAccessDeniedException.class);

        then(imageStorage).should(never()).upload(any(), any());
    }

    @Test
    @DisplayName("표지를 지우면 coverImageUrl 이 비워진다")
    void deleteCover() {
        Trip trip = tripRepository.save(trip(1L));
        trip.updateCoverImage("/uploads/1/trips/x/cover/old.png");

        TripCoverResponse response = tripCoverService.deleteCover(trip.getId(), 1L);

        assertThat(response.coverImageUrl()).isNull();
        assertThat(tripRepository.findById(trip.getId()).orElseThrow().getCoverImageUrl()).isNull();
    }

    private static Trip trip(Long memberId) {
        LocalDate today = LocalDate.now();
        return Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(today.minusDays(10))
                .endDate(today.minusDays(7))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("image", "cover.png", "image/png", "png-bytes".getBytes());
    }
}
