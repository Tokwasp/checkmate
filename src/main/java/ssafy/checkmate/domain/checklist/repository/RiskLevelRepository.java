package ssafy.checkmate.domain.checklist.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;

public interface RiskLevelRepository extends JpaRepository<RiskLevel, Long> {

    Optional<RiskLevel> findByLevel(RiskLevelType level);
}
