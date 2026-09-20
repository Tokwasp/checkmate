package ssafy.checkmate.domain.checklist;

import static ssafy.checkmate.domain.checklist.TestRequestFactory.returnCheckScanRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.entity.CarriageType;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;

/**
 * 카메라로 확인(32·33번) 엔드포인트 테스트 — HTTP 계약만 본다.
 * 목록을 가르고 이름을 찾는 규칙은 {@code ReturnCheckScanServiceTest}의 몫이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReturnCheckScanControllerTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("확인 현황을 조회하면 두 목록과 개수 두 개를 응답한다")
    void getItems() throws Exception {
        Trip trip = createTrip(MEMBER_ID);
        Bag carrier = createBag(trip, "캐리어", CarriageType.CHECKED);
        createCheckedItem(trip, carrier, "여권", "https://s3.example.com/1/a.png");
        createUncheckedItem(trip, carrier, "선글라스");

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.checkedCount").value(1))
                .andExpect(jsonPath("$.data.uncheckedCount").value(1))
                .andExpect(jsonPath("$.data.checkedItems.length()").value(1))
                .andExpect(jsonPath("$.data.checkedItems[0].name").value("여권"))
                .andExpect(jsonPath("$.data.checkedItems[0].imageUrl").value("https://s3.example.com/1/a.png"))
                .andExpect(jsonPath("$.data.checkedItems[0].returnChecked").value(true))
                .andExpect(jsonPath("$.data.checkedItems[0].bagName").value("캐리어"))
                .andExpect(jsonPath("$.data.uncheckedItems.length()").value(1))
                .andExpect(jsonPath("$.data.uncheckedItems[0].name").value("선글라스"))
                .andExpect(jsonPath("$.data.uncheckedItems[0].returnChecked").value(false))
                .andExpect(jsonPath("$.data.uncheckedItems[0].bagName").value("캐리어"))
                .andExpect(jsonPath("$.data.uncheckedItems[0].imageUrl").doesNotExist());
    }

    @Test
    @DisplayName("담은 물품이 없어도 200으로 빈 목록 두 개를 응답한다")
    void getItemsWithNoItems() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedCount").value(0))
                .andExpect(jsonPath("$.data.uncheckedCount").value(0))
                .andExpect(jsonPath("$.data.checkedItems").isEmpty())
                .andExpect(jsonPath("$.data.uncheckedItems").isEmpty());
    }

    @Test
    @DisplayName("확인 현황 조회 시 토큰이 없으면 401을 응답한다")
    void getItemsWithoutToken() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check/items", trip.getId()))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("확인 현황 조회 시 본인 여행이 아니면 403을 응답한다")
    void getItemsOtherMembersTrip() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check/items", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 여행만 조회할 수 있습니다."));
    }

    @Test
    @DisplayName("확인 현황 조회 시 존재하지 않는 여행이면 404를 응답한다")
    void getItemsMissingTrip() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}/return-check/items", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 여행입니다."));
    }

    @Test
    @DisplayName("인식한 이름을 보내면 물품을 체크하고 남은 개수와 함께 응답한다")
    void scan() throws Exception {
        Trip trip = createTrip(MEMBER_ID);
        Bag carrier = createBag(trip, "캐리어", CarriageType.CHECKED);
        BagItem passport = createUncheckedItem(trip, carrier, "여권");
        createUncheckedItem(trip, carrier, "선글라스");

        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID))
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest("여권")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.uncheckedCount").value(1))
                .andExpect(jsonPath("$.data.item.itemId").value(passport.getId()))
                .andExpect(jsonPath("$.data.item.name").value("여권"))
                .andExpect(jsonPath("$.data.item.returnChecked").value(true))
                .andExpect(jsonPath("$.data.item.bagName").value("캐리어"));
    }

    @Test
    @DisplayName("이름이 맞는 미확인 물품이 없으면 200으로 matched false를 응답한다")
    void scanWithoutMatch() throws Exception {
        Trip trip = createTrip(MEMBER_ID);
        Bag carrier = createBag(trip, "캐리어", CarriageType.CHECKED);
        createUncheckedItem(trip, carrier, "선글라스");

        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID))
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest("노트북")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.uncheckedCount").value(1))
                .andExpect(jsonPath("$.data.item").doesNotExist());
    }

    @Test
    @DisplayName("물품 이름 없이 요청하면 400을 응답한다")
    void scanWithoutName() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID))
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest(null)))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("물품 이름은 필수입니다."));
    }

    @Test
    @DisplayName("체크 요청 시 토큰이 없으면 401을 응답한다")
    void scanWithoutToken() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", trip.getId())
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest("여권")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("체크 요청 시 본인 여행이 아니면 403을 응답한다")
    void scanOtherMembersTrip() throws Exception {
        Trip trip = createTrip(MEMBER_ID);

        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_MEMBER_ID))
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest("여권")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인의 여행만 수정할 수 있습니다."));
    }

    @Test
    @DisplayName("체크 요청 시 존재하지 않는 여행이면 404를 응답한다")
    void scanMissingTrip() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/return-check/scan", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(MEMBER_ID))
                        .content(objectMapper.writeValueAsString(returnCheckScanRequest("여권")))
                        .contentType(MediaType.APPLICATION_JSON))
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

    private BagItem createUncheckedItem(Trip trip, Bag bag, String name) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.packInto(bag.getId(), null, "기타");
        return bagItemRepository.saveAndFlush(item);
    }

    /** 감사(auditing)가 updatedAt을 덮어쓰므로 체크 표시는 벌크 업데이트로 박는다. */
    private BagItem createCheckedItem(Trip trip, Bag bag, String name, String imageUrl) {
        BagItem item = BagItem.create(trip.getId(), MEMBER_ID, name, "기타", 1, bag.getId());
        item.packInto(bag.getId(), imageUrl, "기타");
        BagItem saved = bagItemRepository.saveAndFlush(item);

        entityManager.createQuery(
                        "UPDATE BagItem b SET b.returnChecked = true, b.updatedAt = :checkedAt WHERE b.id = :id")
                .setParameter("checkedAt", LocalDateTime.of(2026, 9, 20, 10, 0))
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
        return saved;
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
