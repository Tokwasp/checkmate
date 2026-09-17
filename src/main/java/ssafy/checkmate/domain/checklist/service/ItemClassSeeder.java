package ssafy.checkmate.domain.checklist.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ssafy.checkmate.domain.checklist.entity.ItemClass;
import ssafy.checkmate.domain.checklist.repository.ItemClassRepository;

/**
 * 물품 마스터 초기 데이터. ID는 온디바이스 모델의 클래스 ID(AI/classes.txt 순서)와 같아야 해서
 * 자동 생성하지 않고 직접 넣는다.
 *
 * 테이블이 비어 있을 때만 넣는다 — ddl-auto가 create-drop인 개발/테스트에서는 매 기동마다,
 * 운영처럼 스키마가 유지되는 환경에서는 처음 한 번만 실행된다. 클래스가 추가되면 이 목록에
 * 줄을 더하고, 이미 데이터가 있는 DB에는 별도 마이그레이션으로 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemClassSeeder implements ApplicationRunner {

    private static final String DOCUMENT = "여권·서류";
    private static final String ELECTRONICS = "전자기기";
    private static final String CLOTHING = "의류";
    private static final String TOILETRIES = "세면·위생";
    private static final String MEDICINE = "의약품";
    private static final String ETC = "기타";

    private static final List<ItemClass> ITEM_CLASSES = List.of(
            ItemClass.of(0L, "여권", DOCUMENT),
            ItemClass.of(1L, "지갑", DOCUMENT),
            ItemClass.of(2L, "노트북", ELECTRONICS),
            ItemClass.of(3L, "충전기", ELECTRONICS),
            ItemClass.of(4L, "보조배터리", ELECTRONICS),
            ItemClass.of(5L, "이어폰", ELECTRONICS),
            ItemClass.of(6L, "상의", CLOTHING),
            ItemClass.of(7L, "하의", CLOTHING),
            ItemClass.of(8L, "양말", CLOTHING),
            ItemClass.of(9L, "신발류", CLOTHING),
            ItemClass.of(10L, "모자", CLOTHING),
            ItemClass.of(11L, "선글라스", CLOTHING),
            ItemClass.of(12L, "우산", ETC),
            ItemClass.of(13L, "가위", ETC),
            ItemClass.of(14L, "칼", ETC),
            ItemClass.of(15L, "태블릿", ELECTRONICS),
            ItemClass.of(16L, "삼각대", ELECTRONICS),
            ItemClass.of(17L, "상비약", MEDICINE),
            ItemClass.of(18L, "장갑", CLOTHING),
            ItemClass.of(19L, "칫솔", TOILETRIES),
            ItemClass.of(20L, "치약", TOILETRIES),
            ItemClass.of(21L, "텀블러", ETC),
            ItemClass.of(22L, "백팩", ETC));

    private final ItemClassRepository itemClassRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (itemClassRepository.count() > 0) {
            return;
        }
        itemClassRepository.saveAll(ITEM_CLASSES);
        log.info("물품 마스터 {}건을 넣었다", ITEM_CLASSES.size());
    }
}
