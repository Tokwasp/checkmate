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
public class RiskLevel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RiskLevelType level;

    private String description;

    @Builder(access = AccessLevel.PRIVATE)
    private RiskLevel(RiskLevelType level, String description) {
        this.level = level;
        this.description = description;
    }

    public static RiskLevel create(RiskLevelType level, String description) {
        return RiskLevel.builder()
                .level(level)
                .description(description)
                .build();
    }
}
