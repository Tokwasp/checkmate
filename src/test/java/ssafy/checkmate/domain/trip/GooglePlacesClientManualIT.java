package ssafy.checkmate.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ssafy.checkmate.domain.trip.place.GooglePlacesClient;
import ssafy.checkmate.domain.trip.place.PlaceDetails;
import ssafy.checkmate.domain.trip.place.PlaceSuggestion;

/**
 * 실제 Google Places API(New)를 호출하는 수동 확인용 테스트. mock을 전혀 쓰지 않는다.
 *
 * - {@code test} 프로필을 쓰지 않는다(일부러 @ActiveProfiles 없음) — application.yaml의
 *   ${GOOGLE_PLACES_KEY}가 로컬 .env 값으로 그대로 해석된다.
 * - 카드 미보유로 GCP 결제 계정을 활성화하지 못해, 이 팀에서는 아직 한 번도 실행해서
 *   확인하지 못했다(docs/travel/docs.md §9-8). 카드 등록이 가능한 팀원이 실제 키를
 *   .env에 넣고 이 테스트로 요청/응답 필드명(X-Goog-FieldMask, displayName/location 등)이
 *   맞는지 확인한 뒤, 결과를 docs.md에 반영해야 한다.
 * - 확인 포인트 추가(fix/auto-research): autocomplete에 includedPrimaryTypes
 *   (locality/administrative_area_level_1/country) 제한을 걸었다 — 실제 호출 시
 *   업체·랜드마크·주소 후보가 걸러지고 도시/행정구역/국가만 나오는지 함께 확인할 것.
 *
 * 실행: 아래 @Disabled를 지우고
 *   ./gradlew test --tests "ssafy.checkmate.domain.trip.GooglePlacesClientManualIT"
 */
@Disabled("실제 Google Places 호출 — 카드 등록 후 .env 값으로 수동 확인할 때만 주석 해제")
@SpringBootTest
class GooglePlacesClientManualIT {

    @Autowired
    private GooglePlacesClient googlePlacesClient;

    @Test
    void autocompleteReturnsSuggestionsForTokyo() {
        List<PlaceSuggestion> suggestions = googlePlacesClient.autocomplete("도쿄", "manual-test-session");

        System.out.println("[Google Places autocomplete 응답]");
        suggestions.forEach(s -> System.out.println("- " + s.getPlaceId() + " : " + s.getDescription()));

        assertThat(suggestions).isNotEmpty();
    }

    @Test
    void getDetailsReturnsCoordinatesForTokyo() {
        List<PlaceSuggestion> suggestions = googlePlacesClient.autocomplete("도쿄", "manual-test-session");
        String placeId = suggestions.get(0).getPlaceId();

        PlaceDetails details = googlePlacesClient.getDetails(placeId, "manual-test-session");

        System.out.println("[Google Places details 응답] " + details.getName()
                + " (" + details.getLatitude() + ", " + details.getLongitude() + ")");

        assertThat(details.getName()).isNotBlank();
        assertThat(details.getLatitude()).isNotNull();
        assertThat(details.getLongitude()).isNotNull();
    }
}
