package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ssafy.checkmate.domain.checklist.service.ChecklistCategoryInferrer;
import ssafy.checkmate.domain.checklist.service.GmsClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChecklistCategoryInferrerTest {

    @Mock
    private GmsClient gmsClient;

    @Test
    @DisplayName("GMS 응답 JSON을 이름 → 카테고리로 변환한다")
    void infer() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                [{"name": "선크림", "category": "생활용품"},
                 {"name": "멀티어댑터", "category": "전자기기"}]
                """);

        // when
        Map<String, String> categories = inferrer().infer(List.of("선크림", "멀티어댑터"));

        // then
        assertThat(categories)
                .containsEntry("선크림", "생활용품")
                .containsEntry("멀티어댑터", "전자기기");
    }

    @Test
    @DisplayName("마크다운 코드블록으로 감싸서 응답해도 JSON만 파싱한다")
    void inferWithCodeFence() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                ```json
                [{"name": "여권", "category": "서류"}]
                ```
                """);

        // when
        Map<String, String> categories = inferrer().infer(List.of("여권"));

        // then
        assertThat(categories).containsEntry("여권", "서류");
    }

    @Test
    @DisplayName("이름의 앞뒤 공백은 떼고 같은 이름은 한 번만 물어본다")
    void inferStripsAndDeduplicatesNames() {
        // given
        given(gmsClient.chat(anyString(), anyString()))
                .willReturn("[{\"name\": \"우산\", \"category\": \"생활용품\"}]");

        // when
        Map<String, String> categories = inferrer().infer(List.of(" 우산", "우산 ", "우산"));

        // then
        assertThat(categories).containsEntry("우산", "생활용품");
        verify(gmsClient).chat(anyString(), eq("[\"우산\"]"));
    }

    @Test
    @DisplayName("다섯 가지 카테고리 밖의 값은 버린다")
    void inferIgnoresUnknownCategory() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("""
                [{"name": "선크림", "category": "화장품"},
                 {"name": "여권", "category": "서류"}]
                """);

        // when
        Map<String, String> categories = inferrer().infer(List.of("선크림", "여권"));

        // then
        assertThat(categories).containsOnlyKeys("여권");
    }

    @Test
    @DisplayName("추론할 이름이 없으면 GMS를 부르지 않는다")
    void inferWithoutNames() {
        // when
        Map<String, String> categories = inferrer().infer(List.of());

        // then
        assertThat(categories).isEmpty();
        verify(gmsClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("GMS 호출이 실패하면 빈 맵을 반환한다")
    void inferWhenGmsFails() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willThrow(new RuntimeException("gms down"));

        // when
        Map<String, String> categories = inferrer().infer(List.of("선크림"));

        // then
        assertThat(categories).isEmpty();
    }

    @Test
    @DisplayName("응답이 JSON 형식이 아니면 빈 맵을 반환한다")
    void inferWithInvalidJson() {
        // given
        given(gmsClient.chat(anyString(), anyString())).willReturn("이것은 JSON이 아닙니다");

        // when
        Map<String, String> categories = inferrer().infer(List.of("선크림"));

        // then
        assertThat(categories).isEmpty();
    }

    private ChecklistCategoryInferrer inferrer() {
        return new ChecklistCategoryInferrer(gmsClient, new ObjectMapper());
    }
}
