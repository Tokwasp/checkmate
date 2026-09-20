package ssafy.checkmate.domain.trip.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.trip.dto.response.TripCoverResponse;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.exception.InvalidCoverImageException;
import ssafy.checkmate.domain.trip.exception.TripAccessDeniedException;
import ssafy.checkmate.domain.trip.exception.TripNotFoundException;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.storage.ImageStorage;

/**
 * 여행 표지 사진 — 마이페이지 여행 기록 폴라로이드에 쓰인다.
 *
 * 프로필 사진({@code MemberPhotoService})과 같은 구조다. 저장소는 스캔 누끼·프로필과 같은
 * {@link ImageStorage}, 경로는 회원 폴더 아래 {@code trips/{tripId}/cover}. 이전 파일은 지우지 않는다
 * (저장소에 삭제가 없음 — 프로필 사진과 같은 판단).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripCoverService {

    private final TripRepository tripRepository;
    private final ImageStorage imageStorage;

    /** 표지를 올리고 여행의 coverImageUrl 을 바꾼다. 본인 여행이 아니면 403, 없으면 404. */
    @Transactional
    public TripCoverResponse updateCover(Long tripId, Long memberId, MultipartFile image) {
        validate(image);
        Trip trip = findTripOwnedBy(tripId, memberId);
        String url = imageStorage.upload(image, memberId + "/trips/" + tripId + "/cover");
        trip.updateCoverImage(url);
        return TripCoverResponse.of(trip);
    }

    /** 표지를 지운다 — coverImageUrl 을 비워 기본 그림으로 돌아간다. */
    @Transactional
    public TripCoverResponse deleteCover(Long tripId, Long memberId) {
        Trip trip = findTripOwnedBy(tripId, memberId);
        trip.updateCoverImage(null);
        return TripCoverResponse.of(trip);
    }

    private Trip findTripOwnedBy(Long tripId, Long memberId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(TripNotFoundException::new);
        if (!trip.isOwnedBy(memberId)) {
            throw new TripAccessDeniedException("본인의 여행만 수정할 수 있습니다.");
        }
        return trip;
    }

    /** 비어 있거나 이미지가 아니면 거절한다. 크기 상한은 서블릿 멀티파트 설정이 먼저 막는다. */
    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidCoverImageException();
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidCoverImageException();
        }
    }
}
