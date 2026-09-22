package ssafy.checkmate.domain.checklist.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.ItemClass;

public interface ItemClassRepository extends JpaRepository<ItemClass, Long> {
}
