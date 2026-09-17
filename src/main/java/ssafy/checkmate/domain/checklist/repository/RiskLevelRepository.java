package ssafy.checkmate.domain.checklist.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;

public interface RiskLevelRepository extends JpaRepository<RiskLevel, Long> {
}
