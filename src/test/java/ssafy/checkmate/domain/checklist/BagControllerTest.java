package ssafy.checkmate.domain.checklist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("가방을 생성하면 200과 가방 정보를 응답한다")
    void createBag() throws Exception {
        // given
        Trip trip = createTrip(1L);

        // when // then
        mockMvc.perform(post("/api/v1/trips/{tripId}/bags", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"내 캐리어\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.name").value("내 캐리어"))
                .andExpect(jsonPath("$.data.type").value("CARRIER"))
                .andExpect(jsonPath("$.data.carriageType").value("NONE"));
    }

    @Test
    @DisplayName("가방 이름이 없으면 400과 검증 메시지를 응답한다")
    void createBagWithoutName() throws Exception {
        // given
        Trip trip = createTrip(1L);

        // when // then
        mockMvc.perform(post("/api/v1/trips/{tripId}/bags", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("가방 이름이 누락되었습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 여행에 가방을 생성하면 404를 응답한다")
    void createBagWithNoTrip() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/bags", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"내 캐리어\"}"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
    }

    @Test
    @DisplayName("본인의 여행이 아니면 가방을 생성할 수 없다")
    void createBagWithNotOwner() throws Exception {
        Trip trip = createTrip(1L);

        mockMvc.perform(post("/api/v1/trips/{tripId}/bags", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"내 캐리어\"}"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("가방 정보를 수정한다")
    void updateBag() throws Exception {
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        mockMvc.perform(patch("/api/v1/bags/{bagId}", bag.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carriageType\":\"위탁\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carriageType").value("위탁"));
    }

    @Test
    @DisplayName("기본 가방을 수정하려 하면 400을 응답한다")
    void updateDefaultBag() throws Exception {
        Trip trip = createTrip(1L);
        Bag defaultBag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));

        mockMvc.perform(patch("/api/v1/bags/{bagId}", defaultBag.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이름변경\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기본 가방은 수정할 수 없습니다."));
    }

    @Test
    @DisplayName("가방을 삭제한다")
    void deleteBag() throws Exception {
        Trip trip = createTrip(1L);
        Bag bag = bagRepository.save(Bag.create(trip.getId(), 1L, "내 캐리어"));

        mockMvc.perform(delete("/api/v1/bags/{bagId}", bag.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private Trip createTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .purposes(List.of("휴가"))
                .build());
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
