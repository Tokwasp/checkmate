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
 * 맞췄다. 이번 티켓은 departureChecked만 실제로 쓴다 — departureCheckedByCamera,
 * imageUrl, returnChecked, returnCheckedByCamera는 이후 AR 티켓/return-check 티켓을
 * 위해 컬럼만 미리 마련해둔 예약 필드다.
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

    private boolean departureCheckedByCamera;

    private String imageUrl;

    private boolean returnChecked;

    private boolean returnCheckedByCamera;

    @Builder(access = AccessLevel.PRIVATE)
    private BagItem(Long tripId, Long memberId, String name, String category, int quantity, Long bagId) {
        this.tripId = tripId;
        this.memberId = memberId;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.bagId = bagId;
        this.departureChecked = false;
        this.departureCheckedByCamera = false;
        this.imageUrl = null;
        this.returnChecked = false;
        this.returnCheckedByCamera = false;
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
}
