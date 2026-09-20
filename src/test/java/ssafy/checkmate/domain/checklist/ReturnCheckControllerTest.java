package ssafy.checkmate.domain.checklist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;

/**
 * 귀가 전 점검 현황 조회(31번) 엔드포인트 테스트 — HTTP 계약(경로·상태 코드·JSON 구조)만 본다.
 * 목록을 거르고 묶는 규칙은 {@code ReturnCheckServiceTest}의 몫이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReturnCheckControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("귀가 전 점검 현황을 조회하면 여행 머리글·합계·가방별 물품을 응답한다")
    void getReturnCheck() throws Exception {
        Trip trip = createTrip(MEMBER_ID);
        Bag cabin = createBag(trip, "기내백", CarriageType.CABIN);
        createItem(trip, cabin, "보조배터리", "https://s3.example.com/1/a.png", true);
        createItem(trip, cabin, "여권", null, false);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.tripId").value(trip.getId()))
                .andExpect(jsonPath("$.data.country").value("일본"))
                .andExpect(jsonPath("$.data.region").value("도쿄"))
                .andExpect(jsonPath("$.data.startDate").value("2026-09-16"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.arrivalAirline").value("대한항공"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.returnCheckedCount").value(1))
                .andExpect(jsonPath("$.data.bags.length()").value(2))
                .andExpect(jsonPath("$.data.bags[0].bagId").doesNotExist())
                .andExpect(jsonPath("$.data.bags[0].name").value("전체"))
                .andExpect(jsonPath("$.data.bags[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.bags[1].bagId").value(cabin.getId()))
                .andExpect(jsonPath("$.data.bags[1].name").value("기내백"))
                .andExpect(jsonPath("$.data.bags[1].bagType").value("CARRIER"))
                .andExpect(jsonPath("$.data.bags[1].carriageType").value("기내"))
                .andExpect(jsonPath("$.data.bags[1].totalCount").value(2))
                .andExpect(jsonPath("$.data.bags[1].returnCheckedCount").value(1))
                .andExpect(jsonPath("$.data.bags[1].items[0].name").value("보조배터리"))
                .andExpect(jsonPath("$.data.bags[1].items[0].imageUrl").value("https://s3.example.com/1/a.png"))
                .andExpect(jsonPath("$.data.bags[1].items[0].returnChecked").value(true))
                .andExpect(jsonPath("$.data.bags[1].items[0].bagName").value("기내백"))
                .andExpect(jsonPath("$.data.bags[1].items[1].name").value("여권"))
                .andExpect(jsonPath("$.data.bags[1].items[1].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.bags[1].items[1].returnChecked").value(false));
    }

    @Test
    @DisplayName("담은 물품이 없어도 200으로 '전체' 가방만 응답한다")
    void getReturnCheckWithNoItems() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.bags.length()").value(1))
                .andExpect(jsonPath("$.data.bags[0].name").value("전체"))
                .andExpect(jsonPath("$.data.bags[0].items").isEmpty());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 응답한다")
    void getReturnCheckWithoutToken() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check", trip.getId()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("본인 여행이 아니면 403을 응답한다")
    void getReturnCheckOtherMembersTrip() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 여행만 조회할 수 있습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 여행이면 404를 응답한다")
    void getReturnCheckMissingTrip() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
    }

    private Trip createTrip(Long memberId) {
        return tripRepository.save(Trip.builder()
                .memberId(memberId)
                .country("일본")
                .region("도쿄")
                .startDate(LocalDate.of(2026, 9, 16))
                .endDate(LocalDate.of(2026, 9, 20))
                .purposes(List.of("휴가"))
                .hasFlight(true)
                .departureAirline("아시아나항공")
                .arrivalAirline("대한항공")
                .latitude(35.6895)
                .longitude(139.6917)
                .build());
    }

    private Bag createBag(Trip trip, String name, CarriageType carriageType) {
        Bag bag = Bag.create(trip.getId(), MEMBER_ID, name);
        bag.changeCarriageType(carriageType);
        return bagRepository.save(bag);
    }

    private BagItem createItem(Trip trip, Bag bag, String name, String imageUrl, boolean returnChecked) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.packInto(bag.getId(), imageUrl, "기타");
        ReflectionTestUtils.setField(item, "returnChecked", returnChecked);
        return bagItemRepository.saveAndFlush(item);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
