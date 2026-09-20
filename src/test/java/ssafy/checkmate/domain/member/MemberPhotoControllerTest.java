package ssafy.checkmate.domain.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.member.entity.Member;
import ssafy.checkmate.domain.member.entity.Provider;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.member.repository.MemberRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;
import ssafy.checkmate.global.storage.ImageStorage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberPhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("사진을 올리면 200 과 갱신된 회원 정보를 응답한다")
    void updatePhoto() throws Exception {
        Member member = memberRepository.save(member());
        given(imageStorage.upload(any(), any())).willReturn("/uploads/" + member.getId() + "/profile/abc.png");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/photo")
                        .file(new MockMultipartFile("image", "me.png", "image/png", "png-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.getId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(member.getId()))
                .andExpect(jsonPath("$.data.profileImageUrl").value("/uploads/" + member.getId() + "/profile/abc.png"));
    }

    @Test
    @DisplayName("파일이 없거나 이미지가 아니면 400 이다")
    void updatePhotoBadRequest() throws Exception {
        Member member = memberRepository.save(member());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/photo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/photo")
                        .file(new MockMultipartFile("image", "note.txt", "text/plain", "hello".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진을 지우면 200 과 profileImageUrl 이 null 인 회원 정보를 응답한다")
    void deletePhoto() throws Exception {
        Member member = memberRepository.save(member());
        member.updateProfileImage("/uploads/x/profile/old.png");

        mockMvc.perform(delete("/api/v1/members/me/photo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.getId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void unauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/photo"))
                .andExpect(status().isUnauthorized());
    }

    private static Member member() {
        return Member.builder()
                .email("hodu@test.local")
                .nickname("호두")
                .profileImageUrl(null)
                .provider(Provider.GOOGLE)
                .build();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
