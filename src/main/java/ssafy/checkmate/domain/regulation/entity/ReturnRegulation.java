package ssafy.checkmate.domain.regulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * 귀국 규정 안내(34번)의 규정 한 줄. 여행이나 물품과 무관한 고정 안내라서 어떤 회원·여행과도
 * 연결하지 않는다.
 *
 * 초기 데이터는 {@link ssafy.checkmate.domain.regulation.service.ReturnRegulationSeeder}가
 * 넣는다. {@code guide}는 응답에 그대로 나가는 문구이므로 화면에서 읽히는 말투로 적는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRegulation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RegulationPart part;

    /** 파트 안에서의 표시 순서. 응답은 이 값 오름차순이다. */
    private int sortOrder;

    private String name;

    @Enumerated(EnumType.STRING)
    private RegulationLevel level;

    @Column(length = 500)
    private String guide;

    private ReturnRegulation(RegulationPart part, int sortOrder, String name, RegulationLevel level, String guide) {
        this.part = part;
        this.sortOrder = sortOrder;
        this.name = name;
        this.level = level;
        this.guide = guide;
    }

    public static ReturnRegulation of(RegulationPart part, int sortOrder, String name, RegulationLevel level,
            String guide) {
        return new ReturnRegulation(part, sortOrder, name, level, guide);
    }

    public boolean belongsTo(RegulationPart part) {
        return this.part == part;
    }
}
