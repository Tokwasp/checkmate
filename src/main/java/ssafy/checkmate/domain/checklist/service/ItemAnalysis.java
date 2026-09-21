package ssafy.checkmate.domain.checklist.service;

import java.util.List;

/**
 * 사진에서 읽어 낸 물품 정보(35번 1단계, VLM 결과).
 *
 * {@code ingredients}는 눈으로 추정한 값이다 — 포장 라벨이 안 보이면 틀릴 수 있고, 이 값이 판정의
 * 근거가 되므로 응답에도 그대로 내려 사용자가 잘못 읽었는지 알아챌 수 있게 한다.
 */
public record ItemAnalysis(String name, String category, List<String> ingredients, String state) {

    public ItemAnalysis {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }
}
