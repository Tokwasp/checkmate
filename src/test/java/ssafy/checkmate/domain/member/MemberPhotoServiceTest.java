package ssafy.checkmate.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.dto.response.MemberResponse;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.exception.InvalidProfileImageException;
import ssafy.checkmate.domain.member.exception.MemberNotFoundException;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.domain.member.service.MemberPhotoService;
import ssafy.checkmate.global.storage.ImageStorage;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberPhotoServiceTest {

    @Autowired
    private MemberPhotoService memberPhotoService;

    @Autowired
    private MemberRepository memberRepository;

    /** 실제 저장소(로컬 파일)에 쓰지 않도록 흉내 낸다 — 어디에 어떤 폴더로 올리는지만 본다 */
    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("사진을 올리면 회원 폴더의 profile 아래 저장하고 profileImageUrl 을 바꾼다")
    void updatePhoto() {
        // given
        Member member = memberRepository.save(member("hodu@test.local"));
        String expectedUrl = "/uploads/" + member.getId() + "/profile/abc.png";
        given(imageStorage.upload(any(), eq(member.getId() + "/profile"))).willReturn(expectedUrl);

        // when
        MemberResponse response = memberPhotoService.updatePhoto(member.getId(), png("me.png"));

        // then
        assertThat(response.profileImageUrl()).isEqualTo(expectedUrl);
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getProfileImageUrl()).isEqualTo(expectedUrl);
    }

    @Test
    @DisplayName("비어 있거나 이미지가 아닌 파일은 거절하고 저장소를 건드리지 않는다")
    void updatePhotoRejectsInvalidImage() {
        Member member = memberRepository.save(member("hodu@test.local"));

        assertThatThrownBy(() -> memberPhotoService.updatePhoto(member.getId(), null))
                .isInstanceOf(InvalidProfileImageException.class);
        assertThatThrownBy(() -> memberPhotoService.updatePhoto(member.getId(),
                new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(InvalidProfileImageException.class);
        assertThatThrownBy(() -> memberPhotoService.updatePhoto(member.getId(),
                new MockMultipartFile("image", "note.txt", "text/plain", "hello".getBytes())))
                .isInstanceOf(InvalidProfileImageException.class);

        then(imageStorage).should(never()).upload(any(), any());
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("없는 회원이면 404 이고 저장소를 건드리지 않는다")
    void updatePhotoMemberNotFound() {
        assertThatThrownBy(() -> memberPhotoService.updatePhoto(999_999L, png("me.png")))
                .isInstanceOf(MemberNotFoundException.class);
        then(imageStorage).should(never()).upload(any(), any());
    }

    @Test
    @DisplayName("사진을 지우면 profileImageUrl 이 비워진다")
    void deletePhoto() {
        Member member = memberRepository.save(member("hodu@test.local"));
        member.updateProfileImage("/uploads/1/profile/old.png");

        MemberResponse response = memberPhotoService.deletePhoto(member.getId());

        assertThat(response.profileImageUrl()).isNull();
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getProfileImageUrl()).isNull();
    }

    private static Member member(String email) {
        return Member.builder()
                .email(email)
                .nickname("호두")
                .profileImageUrl(null)
                .provider(Provider.GOOGLE)
                .build();
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("image", name, "image/png", "png-bytes".getBytes());
    }
}
