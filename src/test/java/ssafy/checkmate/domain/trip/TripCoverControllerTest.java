package ssafy.checkmate.domain.trip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;
import ssafy.checkmate.global.storage.ImageStorage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripCoverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("표지를 올리면 200 과 바뀐 URL 을 응답하고, 여행 상세·목록에도 실린다")
    void updateCover() throws Exception {
        Trip trip = tripRepository.save(trip(1L));
        String url = "/uploads/1/trips/" + trip.getId() + "/cover/abc.png";
        given(imageStorage.upload(any(), any())).willReturn(url);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/trips/{tripId}/cover", trip.getId())
                        .file(new MockMultipartFile("image", "cover.png", "image/png", "png-bytes".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.data.coverImageUrl").value(url));

        // 마이페이지 여행 기록·홈 카드가 읽는 두 응답에 그대로 실린다
        mockMvc.perform(get("/api/v1/trips/{tripId}", trip.getId()).header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl").value(url));
        mockMvc.perform(get("/api/v1/trips").header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trips[0].coverImageUrl").value(url));
    }

    @Test
    @DisplayName("파일이 없거나 이미지가 아니면 400, 남의 여행이면 403, 없는 여행이면 404 이다")
    void updateCoverErrors() throws Exception {
        Trip mine = tripRepository.save(trip(1L));
        Trip others = tripRepository.save(trip(2L));
        MockMultipartFile png = new MockMultipartFile("image", "cover.png", "image/png", "png-bytes".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/trips/{tripId}/cover", mine.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/trips/{tripId}/cover", mine.getId())
                        .file(new MockMultipartFile("image", "note.txt", "text/plain", "hello".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/trips/{tripId}/cover", others.getId())
                        .file(png)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/trips/{tripId}/cover", 999_999L)
                        .file(png)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("표지를 지우면 200 과 null URL 을 응답한다")
    void deleteCover() throws Exception {
        Trip trip = tripRepository.save(trip(1L));
        trip.updateCoverImage("/uploads/1/trips/x/cover/old.png");

        mockMvc.perform(delete("/api/v1/trips/{tripId}/cover", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void unauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/trips/{tripId}/cover", 1L))
                .andExpect(status().isUnauthorized());
    }

    private static Trip trip(Long memberId) {
        LocalDate today = LocalDate.now();
        return Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(today.plusDays(1))
                .endDate(today.plusDays(3))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
