package ssafy.checkmate.domain.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ssafy.checkmate.domain.checklist.entity.RiskLevel;
import ssafy.checkmate.domain.checklist.entity.RiskLevelType;
import ssafy.checkmate.domain.checklist.repository.RiskLevelRepository;

@SpringBootTest
@ActiveProfiles("test")
class RiskLevelInitializerTest {

    @Autowired
    private RiskLevelRepository riskLevelRepository;

    @Test
    @DisplayName("앱 구동 시 RiskLevel이 비어 있으면 금지·주의를 시드 데이터로 채운다")
    void seedsForbiddenAndCaution() {
        List<RiskLevel> riskLevels = riskLevelRepository.findAll();

        assertThat(riskLevels).extracting(RiskLevel::getLevel)
                .contains(RiskLevelType.FORBIDDEN, RiskLevelType.CAUTION);
    }
}
