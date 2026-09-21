package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.checklist.service.ItemAnalysis;

/** 사진에서 읽어 낸 물품. 원재료는 추정값이라 화면이 보여줘서 잘못 읽었는지 알아챌 수 있게 한다. */
@Getter
public class QuarantineItemResponse {

    private final String name;
    private final String category;
    private final List<String> ingredients;
    private final String state;

    private QuarantineItemResponse(String name, String category, List<String> ingredients, String state) {
        this.name = name;
        this.category = category;
        this.ingredients = ingredients;
        this.state = state;
    }

    public static QuarantineItemResponse of(ItemAnalysis item) {
        return new QuarantineItemResponse(item.name(), item.category(), item.ingredients(), item.state());
    }
}
