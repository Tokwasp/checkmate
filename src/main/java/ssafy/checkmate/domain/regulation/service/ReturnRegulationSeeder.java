package ssafy.checkmate.domain.regulation.service;

import static ssafy.checkmate.domain.regulation.entity.RegulationLevel.ALLOWED;
import static ssafy.checkmate.domain.regulation.entity.RegulationLevel.PROHIBITED;
import static ssafy.checkmate.domain.regulation.entity.RegulationLevel.RESTRICTED;
import static ssafy.checkmate.domain.regulation.entity.RegulationPart.CABIN;
import static ssafy.checkmate.domain.regulation.entity.RegulationPart.QUARANTINE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.regulation.entity.ReturnRegulation;
import ssafy.checkmate.domain.regulation.repository.ReturnRegulationRepository;

/**
 * 귀국 규정 안내(34번) 초기 데이터 — 기내 반입 5건, 한국 입국 검역 5건.
 *
 * 근거는 항공보안법 시행규칙 별표 1, 국토교통부 보조배터리 기내 안전기준(2026.4.20 시행),
 * 관세청 여행자 휴대품 통관, 인천공항 동식물 검역 안내, 인천공항본부세관 의약품·건강기능식품
 * 안내, CITES 업무처리지침이다. 참고용 요약이라 법적 효력은 각 규정 원문에 있다.
 *
 * 테이블이 비어 있을 때만 넣는다 — ddl-auto가 create-drop인 개발/테스트에서는 매 기동마다,
 * 운영처럼 스키마가 유지되는 환경에서는 처음 한 번만 실행된다. 이미 데이터가 있는 DB의 문구를
 * 고치거나 항목을 늘릴 때는 이 목록만 고쳐서는 반영되지 않으므로 별도 마이그레이션이 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnRegulationSeeder implements ApplicationRunner {

    /**
     * 실행할 때마다 새 객체를 만든다. 엔티티를 상수로 들고 있으면 저장 때 채워진 id가 남아, 스키마가
     * 다시 만들어지는 환경(테스트 컨텍스트 재기동)에서 이미 저장된 것으로 취급돼 삽입이 꼬인다.
     */
    private static List<ReturnRegulation> regulations() {
        return List.of(
                // 기내 반입
                ReturnRegulation.of(CABIN, 1, "보조배터리", ALLOWED,
                        "160Wh(약 43,000mAh) 이하만 1인 2개까지 기내 반입 가능합니다. "
                                + "100~160Wh는 항공사 승인이 필요하며, 위탁 수하물에는 넣을 수 없습니다."),
                ReturnRegulation.of(CABIN, 2, "액체·젤·스프레이(치약, 화장품 등)", RESTRICTED,
                        "용기 하나당 100ml 이하만 기내 반입 가능합니다. 100ml를 넘으면 위탁 수하물로 부쳐야 합니다."),
                ReturnRegulation.of(CABIN, 3, "칼", PROHIBITED,
                        "기내 반입 불가하므로 위탁 수하물에 넣어야 합니다. "
                                + "접이식칼·다목적칼·커터칼은 길이와 무관하게 금지되며, 날 길이 6cm 초과 칼도 금지입니다."),
                ReturnRegulation.of(CABIN, 4, "가위", PROHIBITED,
                        "날 길이 6cm 초과는 기내 반입 불가하며 위탁 수하물로 부쳐야 합니다. 6cm 이하는 반입 가능합니다."),
                ReturnRegulation.of(CABIN, 5, "고도주", RESTRICTED,
                        "도수 24도 초과 70도 이하는 1인당 5L까지 위탁 가능합니다. 70도 초과는 기내·위탁 모두 금지입니다."),

                // 한국 입국 검역
                ReturnRegulation.of(QUARANTINE, 1, "육류·육가공품·유제품(육포, 소시지, 햄, 치즈 등)", PROHIBITED,
                        "입국장에서 검역관에게 반드시 신고해야 합니다. 수출국 검역증명서가 없으면 반입 불가합니다."),
                ReturnRegulation.of(QUARANTINE, 2, "생과일·채소·식물·흙", PROHIBITED,
                        "생과일과 열매채소는 반입 불가하며, 식물·종자·흙은 검역관 신고가 필수입니다. "
                                + "김치·떡 등 가열 조리된 가공식품은 검사 대상이 아닙니다."),
                ReturnRegulation.of(QUARANTINE, 3, "의약품·건강기능식품", RESTRICTED,
                        "자가 사용 목적이면 6병까지 별도 요건 확인 없이 통관 가능합니다."),
                ReturnRegulation.of(QUARANTINE, 4, "주류·담배·향수", RESTRICTED,
                        "면세 범위는 술 2L(총 $400) 이하, 담배 200개비, 전자담배 액상 20ml, 향수 100ml입니다."),
                ReturnRegulation.of(QUARANTINE, 5, "멸종위기종 기념품(악어·뱀 가죽, 산호, 상아, 거북 제품 등)", PROHIBITED,
                        "허가서 없이는 반입 불가합니다. 구매 전 CITES 해당 여부를 확인해야 합니다."));
    }

    private final ReturnRegulationRepository returnRegulationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (returnRegulationRepository.count() > 0) {
            return;
        }
        List<ReturnRegulation> regulations = regulations();
        returnRegulationRepository.saveAll(regulations);
        log.info("귀국 규정 {}건을 넣었다", regulations.size());
    }
}
