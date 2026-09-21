package ssafy.checkmate.domain.regulation.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;

public interface ReturnRegulationRepository extends JpaRepository<ReturnRegulation, Long> {

    /**
     * 전체를 표시 순서대로 읽는다. 파트별로 두 번 조회하지 않는 이유는 10건짜리 고정 표라
     * 한 번에 읽어 메모리에서 나누는 편이 단순하기 때문이다. 정렬은 응답이 파트 안 순서를
     * 그대로 쓸 수 있게 여기서 끝낸다.
     */
    List<ReturnRegulation> findAllByOrderBySortOrderAsc();
}
