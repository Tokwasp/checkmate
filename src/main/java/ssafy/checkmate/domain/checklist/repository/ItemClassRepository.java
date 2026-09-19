package ssafy.checkmate.domain.checklist.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.ItemClass;

public interface ItemClassRepository extends JpaRepository<ItemClass, Long> {

    /**
     * 이름으로 물품 마스터를 찾는다. {@code BagItem}은 인식 당시의 {@code itemClassId}를
     * 저장하지 않고 이름만 들고 있어서, 반입 규정 검사(30번)가 검색 별칭을 찾으려면
     * 이름으로 되짚는 수밖에 없다. 체크리스트로 직접 추가한 물품은 마스터에 없어서
     * 비어 있을 수 있다 — 그때는 별칭 없이 한국어로만 검색한다.
     */
    Optional<ItemClass> findByClassName(String className);
}
