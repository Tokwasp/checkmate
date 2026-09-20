package ssafy.checkmate.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.member.dto.response.MemberResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.exception.InvalidProfileImageException;
import ssafy.checkmate.domain.member.exception.MemberNotFoundException;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.global.storage.ImageStorage;

/**
 * 마이페이지 프로필 사진.
 *
 * 저장은 스캔 누끼와 같은 {@link ImageStorage} 를 쓴다(운영 S3 / 로컬 파일). 회원 폴더 아래
 * {@code profile/} 에 두어 누끼 사진과 섞이지 않게 한다.
 *
 * 이전 사진 파일은 지우지 않는다 — {@link ImageStorage} 에 삭제가 없고, 프론트가 기기에만 두던
 * 사진을 서버로 옮기는 첫 단계라 파일 하나 남는 것보다 삭제 실패로 요청이 깨지는 쪽이 더 나쁘다.
 * 정리가 필요해지면 그때 저장소에 delete 를 붙인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPhotoService {

    private final MemberRepository memberRepository;
    private final ImageStorage imageStorage;

    /** 사진을 올리고 회원의 profileImageUrl 을 바꾼다. 응답은 갱신된 회원 정보. */
    @Transactional
    public MemberResponse updatePhoto(Long memberId, MultipartFile image) {
        validate(image);
        Member member = findMember(memberId);
        String url = imageStorage.upload(image, memberId + "/profile");
        member.updateProfileImage(url);
        return MemberResponse.from(member);
    }

    /** 사진을 지운다 — profileImageUrl 을 비워 기본 아이콘으로 돌아간다. */
    @Transactional
    public MemberResponse deletePhoto(Long memberId) {
        Member member = findMember(memberId);
        member.updateProfileImage(null);
        return MemberResponse.from(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
    }

    /** 비어 있거나 이미지가 아니면 거절한다. 크기 상한은 서블릿 멀티파트 설정이 먼저 막는다. */
    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidProfileImageException();
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidProfileImageException();
        }
    }
}
