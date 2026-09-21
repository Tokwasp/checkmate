package ssafy.checkmate.domain.regulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;
import ssafy.checkmate.domain.regulation.entity.RegulationPart;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;
import ssafy.checkmate.domain.regulation.repository.ReturnRegulationRepository;
import ssafy.checkmate.domain.regulation.service.ReturnRegulationSeeder;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReturnRegulationSeederTest {

    @Autowired
    private ReturnRegulationSeeder returnRegulationSeeder;

    @Autowired
    private ReturnRegulationRepository returnRegulationRepository;

    @Test
    @DisplayName("앱 구동 시 규정 표가 비어 있으면 기내 반입 5건과 한국 입국 검역 5건을 채운다")
    void seedsTenRegulations() {
        List<ReturnRegulation> regulations = returnRegulationRepository.findAll();

        assertThat(regulations).hasSize(10);
        assertThat(regulations).filteredOn(r -> r.belongsTo(RegulationPart.CABIN)).hasSize(5);
        assertThat(regulations).filteredOn(r -> r.belongsTo(RegulationPart.QUARANTINE)).hasSize(5);
    }

    @Test
    @DisplayName("파트마다 sortOrder는 1부터 5까지 겹치지 않게 매겨진다")
    void sortOrdersAreOneToFivePerPart() {
        List<ReturnRegulation> regulations = returnRegulationRepository.findAll();

        assertThat(regulations).filteredOn(r -> r.belongsTo(RegulationPart.CABIN))
                .extracting(ReturnRegulation::getSortOrder)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(regulations).filteredOn(r -> r.belongsTo(RegulationPart.QUARANTINE))
                .extracting(ReturnRegulation::getSortOrder)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("모든 규정에 이름·판정·안내 문구가 비어 있지 않게 들어 있다")
    void everyRegulationHasNameLevelAndGuide() {
        List<ReturnRegulation> regulations = returnRegulationRepository.findAll();

        assertThat(regulations).allSatisfy(regulation -> {
            assertThat(regulation.getName()).isNotBlank();
            assertThat(regulation.getLevel()).isNotNull();
            assertThat(regulation.getGuide()).isNotBlank();
        });
    }

    @Test
    @DisplayName("판정은 보조배터리만 허용이고, 칼·가위·생과일·육류·멸종위기종 기념품은 금지다")
    void levelsFollowTheSpec() {
        List<ReturnRegulation> regulations = returnRegulationRepository.findAll();

        assertThat(regulations).filteredOn(r -> r.getLevel() == RegulationLevel.ALLOWED)
                .extracting(ReturnRegulation::getName).containsExactly("보조배터리");
        assertThat(regulations).filteredOn(r -> r.getLevel() == RegulationLevel.PROHIBITED)
                .extracting(ReturnRegulation::getName)
                .containsExactlyInAnyOrder("칼", "가위",
                        "육류·육가공품·유제품(육포, 소시지, 햄, 치즈 등)",
                        "생과일·채소·식물·흙",
                        "멸종위기종 기념품(악어·뱀 가죽, 산호, 상아, 거북 제품 등)");
    }

    @Test
    @DisplayName("이미 규정이 들어 있으면 다시 실행해도 중복으로 넣지 않는다")
    void doesNotSeedTwice() {
        returnRegulationSeeder.run(null);

        assertThat(returnRegulationRepository.count()).isEqualTo(10);
    }
}
