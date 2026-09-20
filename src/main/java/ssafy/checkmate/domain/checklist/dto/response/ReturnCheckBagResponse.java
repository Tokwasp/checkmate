package ssafy.checkmate.domain.checklist.dto.response;

import java.util.List;
import lombok.Getter;
import ssafy.checkmate.domain.checklist.entity.Bag;

/**
 * 귀가 전 점검(31번)의 가방 한 칸. 화면의 필터 칩 하나에 대응한다.
 *
 * 맨 앞의 합산 가방 '전체'는 {@link #ofAll}로 만든다 — 모든 물품을 한 번 더 담아
 * 화면이 칩을 누를 때 가방들을 직접 합치지 않아도 되게 한다.
 */
@Getter
public class ReturnCheckBagResponse {

    private static final String ALL_BAG_NAME = "전체";

    private final Long bagId;
    private final String name;
    private final String bagType;
    private final String carriageType;
    private final int totalCount;
    private final int returnCheckedCount;
    private final List<ReturnCheckItemResponse> items;

    private ReturnCheckBagResponse(Long bagId, String name, String bagType, String carriageType,
            int totalCount, int returnCheckedCount, List<ReturnCheckItemResponse> items) {
        this.bagId = bagId;
        this.name = name;
        this.bagType = bagType;
        this.carriageType = carriageType;
        this.totalCount = totalCount;
        this.returnCheckedCount = returnCheckedCount;
        this.items = items;
    }

    public static ReturnCheckBagResponse of(Bag bag, List<ReturnCheckItemResponse> items) {
        return new ReturnCheckBagResponse(
                bag.getId(), bag.getName(), bag.getType().name(), BagResponse.carriageTypeOf(bag),
                items.size(), countReturnChecked(items), items);
    }

    /** 모든 가방을 합산한 가짜 '전체' 항목. bagId/bagType/carriageType은 없다(null). */
    public static ReturnCheckBagResponse ofAll(List<ReturnCheckItemResponse> items) {
        return new ReturnCheckBagResponse(
                null, ALL_BAG_NAME, null, null, items.size(), countReturnChecked(items), items);
    }

    /** 개수는 수량 합이 아니라 물품 행 수다 — 화면이 물품 하나당 타일 한 칸이기 때문이다. */
    private static int countReturnChecked(List<ReturnCheckItemResponse> items) {
        return (int) items.stream().filter(ReturnCheckItemResponse::isReturnChecked).count();
    }
}
