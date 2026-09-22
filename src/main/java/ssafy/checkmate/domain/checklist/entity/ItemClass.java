package ssafy.checkmate.domain.checklist.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * 물품 마스터. 온디바이스 모델이 인식할 수 있는 클래스(AI/classes.txt의 23종)마다
 * 카테고리를 미리 저장해 둔 기준 데이터다.
 *
 * ID를 자동 생성하지 않고 직접 넣는다 — 앱이 보내는 클래스 ID와 값이 같아야 하기 때문이다.
 * 초기 데이터는 {@link ssafy.checkmate.domain.checklist.service.ItemClassSeeder}가 넣는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemClass extends BaseEntity {

    @Id
    private Long id;

    private String className;

    private String category;

    private ItemClass(Long id, String className, String category) {
        this.id = id;
        this.className = className;
        this.category = category;
    }

    public static ItemClass of(Long id, String className, String category) {
        return new ItemClass(id, className, category);
    }
}
