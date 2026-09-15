package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bag extends BaseEntity {

    private static final String DEFAULT_BAG_NAME = "NONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tripId;

    private Long memberId;

    private String name;

    @Enumerated(EnumType.STRING)
    private BagType type;

    @Enumerated(EnumType.STRING)
    private CarriageType carriageType;

    @Builder(access = AccessLevel.PRIVATE)
    private Bag(Long tripId, Long memberId, String name, BagType type, CarriageType carriageType) {
        this.tripId = tripId;
        this.memberId = memberId;
        this.name = name;
        this.type = type;
        this.carriageType = carriageType;
    }

    public static Bag create(Long tripId, Long memberId, String name) {
        return Bag.builder()
                .tripId(tripId)
                .memberId(memberId)
                .name(name)
                .type(BagType.CARRIER)
                .carriageType(null)
                .build();
    }

    public static Bag createDefault(Long tripId, Long memberId) {
        return Bag.builder()
                .tripId(tripId)
                .memberId(memberId)
                .name(DEFAULT_BAG_NAME)
                .type(BagType.NONE)
                .carriageType(null)
                .build();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isDefault() {
        return type == BagType.NONE;
    }

    public void rename(String name) {
        if (name != null) {
            this.name = name;
        }
    }

    public void changeCarriageType(CarriageType carriageType) {
        if (carriageType != null) {
            this.carriageType = carriageType;
        }
    }
}
