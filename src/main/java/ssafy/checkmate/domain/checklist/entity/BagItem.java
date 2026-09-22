package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * 체크리스트 항목. ERD의 Checklist(체크 여부·이름·수량)와 BagItem(가방·카테고리)을
 * 하나로 합친 엔티티다 — 항상 1:1로만 존재해서 나눌 이유가 없었다.
 *
 * 체크 상태는 출국 전(패킹)/귀국 전(점검) 두 시점이 독립적이라 컬럼 이름을 대칭으로
 * 맞췄다. 수동 체크든 카메라 인식이든 결과는 같은 컬럼(departureChecked/returnChecked)
 * 하나로 남긴다 — 체크 수단을 구분해서 저장하지 않는다. 이번 티켓은 departureChecked만
 * 실제로 쓴다 — imageUrl, returnChecked는 이후 AR 티켓/return-check 티켓을 위해
 * 컬럼만 미리 마련해둔 예약 필드다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;

    private Long memberId;

    private String name;

    private String category;

    private int quantity;

    private Long bagId;

    private boolean departureChecked;

    private String imageUrl;

    private boolean returnChecked;

    private boolean favorite;

    @Builder(access = AccessLevel.PRIVATE)
    private BagItem(Long tripId, Long memberId, String name, String category, int quantity, Long bagId) {
        this.tripId = tripId;
        this.memberId = memberId;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.bagId = bagId;
        this.departureChecked = false;
        this.imageUrl = null;
        this.returnChecked = false;
        this.favorite = false;
    }

    public static BagItem create(Long tripId, Long memberId, String name, String category, Integer quantity, Long bagId) {
        return BagItem.builder()
                .tripId(tripId)
                .memberId(memberId)
                .name(name)
                .category(category)
                .quantity(quantity == null ? 1 : quantity)
                .bagId(bagId)
                .build();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    /**
     * 카메라 인식(26번)으로 체크리스트에 없던 물품이 새로 담긴 경우.
     * 담긴 상태(departureChecked=true)로 만들어지고 수량은 1로 시작한다.
     */
    public static BagItem createRecognized(Long tripId, Long memberId, String name, String category,
                                           Long bagId, String imageUrl) {
        BagItem item = BagItem.create(tripId, memberId, name, category, 1, bagId);
        item.departureChecked = true;
        item.imageUrl = imageUrl;
        return item;
    }

    /**
     * 전달된 필드만 반영한다. null인 필드는 기존 값을 유지한다.
     * departureChecked만 다루고, returnChecked류는 이 메서드가 절대 건드리지 않는다.
     */
    public void update(String name, Integer quantity, String category, Boolean checked, Long bagId) {
        if (name != null) {
            this.name = name;
        }
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (category != null) {
            this.category = category;
        }
        if (checked != null) {
            this.departureChecked = checked;
        }
        if (bagId != null) {
            this.bagId = bagId;
        }
    }

    public void moveToDefaultBag(Long defaultBagId) {
        this.bagId = defaultBagId;
        this.departureChecked = false;
    }

    /**
     * 가방 이동 API(25번) 전용. moveToDefaultBag과 달리 체크 상태는 유지한다.
     */
    public void moveToBag(Long bagId) {
        this.bagId = bagId;
    }

    /**
     * 카메라 인식(26번)으로 체크리스트의 기존 항목이 가방에 담긴 경우.
     * 수량과 즐겨찾기는 건드리지 않고, 카테고리는 비어 있을 때만 채운다.
     */
    public void packInto(Long bagId, String imageUrl, String category) {
        this.bagId = bagId;
        this.imageUrl = imageUrl;
        this.departureChecked = true;
        if (this.category == null || this.category.isBlank()) {
            this.category = category;
        }
    }

    /**
     * 카메라 인식(26번)으로 같은 가방에 이미 담긴 같은 이름 물건을 다시 인식한 경우.
     * 새 행을 만들지 않고 같은 물건을 하나 더 인식한 것으로 보아 수량만 늘린다.
     */
    public void increaseQuantity(int amount) {
        this.quantity += amount;
    }

    /**
     * 귀가 전 점검(33번). 카메라가 알아본 물품을 "도로 챙겼다"로 표시한다.
     * 담김 여부(departureChecked)는 건드리지 않는다 — 두 시점은 서로 독립이다.
     */
    public void checkReturn() {
        changeReturnChecked(true);
    }

    /**
     * 귀가 전 점검 단건 체크·해제(33-2번). 사용자가 손으로 켜고 끈다.
     * 카메라(33번)가 켠 것도 같은 컬럼이라 이 메서드로 그대로 풀린다 — 체크 수단은 저장하지 않는다.
     */
    public void changeReturnChecked(boolean returnChecked) {
        this.returnChecked = returnChecked;
    }

    /**
     * 인벤토리 물품 수정(27번). 전달된 값만 반영하고 null인 필드는 기존 값을 유지한다.
     */
    public void updatePacking(Integer quantity, Boolean checked) {
        if (quantity != null) {
            this.quantity = quantity;
        }
        if (checked != null) {
            this.departureChecked = checked;
        }
    }
}
