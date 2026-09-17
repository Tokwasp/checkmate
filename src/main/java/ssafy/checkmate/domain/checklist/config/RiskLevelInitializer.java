package ssafy.checkmate.domain.checklist.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;

/**
 * RiskLevel 마스터 데이터(금지·주의)가 비어 있으면 최초 1회 채워 넣는다.
 */
@Component
@RequiredArgsConstructor
public class RiskLevelInitializer implements CommandLineRunner {

    private final RiskLevelRepository riskLevelRepository;

    @Override
    public void run(String... args) {
        if (riskLevelRepository.count() > 0) {
            return;
        }
        riskLevelRepository.save(RiskLevel.create(RiskLevelType.VIOLATION, "반입이 금지된 물품입니다."));
        riskLevelRepository.save(RiskLevel.create(RiskLevelType.WARNING, "반입 시 주의가 필요한 물품입니다."));
    }
}
