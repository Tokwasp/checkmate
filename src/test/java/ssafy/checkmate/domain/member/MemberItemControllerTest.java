package ssafy.checkmate.domain.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.Bag;
import ssafy.checkmate.domain.checklist.entity.BagItem;
import ssafy.checkmate.domain.checklist.repository.BagItemRepository;
import ssafy.checkmate.domain.checklist.repository.BagRepository;
import ssafy.checkmate.domain.member.entity.MemberItem;
import ssafy.checkmate.domain.member.entity.Role;
import ssafy.checkmate.domain.member.repository.MemberItemRepository;
import ssafy.checkmate.domain.trip.entity.Trip;
import ssafy.checkmate.domain.trip.repository.TripRepository;
import ssafy.checkmate.global.auth.JwtTokenProvider;
import ssafy.checkmate.global.storage.ImageStorage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private BagRepository bagRepository;

    @Autowired
    private BagItemRepository bagItemRepository;

    @Autowired
    private MemberItemRepository memberItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("물건 사진을 올리면 200 과 imageUrl 이 채워진 한 줄을 응답한다. 파일 없으면 400, 없는 물건 404")
    void updateImage() throws Exception {
        MemberItem item = memberItemRepository.save(MemberItem.asFavorite(1L, "이어폰", "전자기기"));
        given(imageStorage.upload(any(), any())).willReturn("https://s3/1/items/earbuds.png");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/items/{itemId}/image", item.getId())
                        .file(new MockMultipartFile("image", "cutout.png", "image/png", "png".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(item.getId()))
                .andExpect(jsonPath("$.data.imageUrl").value("https://s3/1/items/earbuds.png"));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/items/{itemId}/image", item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/items/{itemId}/image", 999_999L)
                        .file(new MockMultipartFile("image", "c.png", "image/png", "png".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("내 물건 목록을 조회하면 담은 횟수·즐겨찾기가 담긴 items 를 응답한다")
    void getMyItems() throws Exception {
        // given — 여권 1회 담음, 선글라스는 즐겨찾기만
        Trip trip = tripRepository.save(Trip.builder()
                .memberId(1L)
                .country("일본")
                .region("오사카")
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().minusDays(2))
                .purposes(List.of("휴가"))
                .latitude(34.6937)
                .longitude(135.5023)
                .build());
        Bag bag = bagRepository.save(Bag.createDefault(trip.getId(), 1L));
        BagItem passport = bagItemRepository.save(BagItem.create(trip.getId(), 1L, "여권", "서류", 1, bag.getId()));
        passport.update(null, null, null, true, null);
        memberItemRepository.save(MemberItem.asFavorite(1L, "선글라스", null));

        // when // then
        mockMvc.perform(get("/api/v1/members/me/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("여권"))
                .andExpect(jsonPath("$.data.items[0].category").value("서류"))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(false))
                .andExpect(jsonPath("$.data.items[0].packedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].itemId").isNumber())
                .andExpect(jsonPath("$.data.items[1].name").value("선글라스"))
                .andExpect(jsonPath("$.data.items[1].isFavorite").value(true))
                .andExpect(jsonPath("$.data.items[1].packedCount").value(0));
    }

    @Test
    @DisplayName("다른 회원의 물건은 내 목록에 나오지 않는다")
    void getMyItemsExcludesOthers() throws Exception {
        memberItemRepository.save(MemberItem.asFavorite(2L, "남의 선글라스", null));

        mockMvc.perform(get("/api/v1/members/me/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("토큰이 없으면 401 을 응답한다")
    void getMyItemsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/items"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("즐겨찾기를 켜면 200 과 그 물건 한 줄을 응답한다")
    void setFavorite() throws Exception {
        MemberItem item = memberItemRepository.save(MemberItem.fromPacked(1L, "여권", "서류"));

        mockMvc.perform(patch("/api/v1/members/me/items/{itemId}/favorite", item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(item.getId()))
                .andExpect(jsonPath("$.data.name").value("여권"))
                .andExpect(jsonPath("$.data.isFavorite").value(true))
                .andExpect(jsonPath("$.data.packedCount").value(0));
    }

    @Test
    @DisplayName("즐겨찾기 값이 없으면 400, 없는 물건이면 404, 남의 물건이면 403 이다")
    void setFavoriteErrors() throws Exception {
        MemberItem mine = memberItemRepository.save(MemberItem.fromPacked(1L, "여권", "서류"));
        MemberItem others = memberItemRepository.save(MemberItem.fromPacked(2L, "남의 여권", "서류"));

        mockMvc.perform(patch("/api/v1/members/me/items/{itemId}/favorite", mine.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/members/me/items/{itemId}/favorite", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/members/me/items/{itemId}/favorite", others.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여러 개를 즐겨찾기하면 200 과 갱신된 목록 전체를 응답한다")
    void addFavorites() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/items/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"name\":\"선글라스\",\"category\":\"패션\"},{\"name\":\"보조배터리\"}]}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].name")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("선글라스", "보조배터리")))
                .andExpect(jsonPath("$.data.items[*].isFavorite")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    @DisplayName("즐겨찾기할 물건이 비어 있거나 이름이 없으면 400 이다")
    void addFavoritesBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/items/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/members/me/items/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"name\":\"  \"}]}"))
                .andExpect(status().isBadRequest());
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, Role.USER);
    }
}
