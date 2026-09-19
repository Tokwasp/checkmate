package ssafy.checkmate.domain.checklist.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 반입 규정 검사(30번)의 검색 별칭. 규정 문서가 현지어라 한국어 질의만으로는 매칭이
     * 끊긴다 — 언어별 검색어를 미리 적어두고 질의에 붙인다({@link ItemClass#searchAliases}).
     * 직역뿐 아니라 규정에서 쓰는 분류 용어(칼 → 刃物·凶器, 치약 → 液体物)도 함께 넣는다.
     */
    private static Map<String, String> aliases(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static final List<ItemClass> ITEM_CLASSES = List.of(
            ItemClass.of(0L, "여권", DOCUMENT, aliases(
                    "ja", "パスポート 旅券",
                    "zh-Hans", "护照",
                    "zh-Hant", "護照",
                    "th", "หนังสือเดินทาง",
                    "vi", "hộ chiếu",
                    "en", "passport",
                    "fr", "passeport passport",
                    "it", "passaporto passport")),
            ItemClass.of(1L, "지갑", DOCUMENT, aliases(
                    "ja", "財布",
                    "zh-Hans", "钱包",
                    "zh-Hant", "錢包",
                    "th", "กระเป๋าสตางค์",
                    "vi", "ví tiền",
                    "en", "wallet",
                    "fr", "portefeuille wallet",
                    "it", "portafoglio wallet")),
            ItemClass.of(2L, "노트북", ELECTRONICS, aliases(
                    "ja", "ノートパソコン 電子機器",
                    "zh-Hans", "笔记本电脑 电子设备",
                    "zh-Hant", "筆記型電腦 電子設備",
                    "th", "แล็ปท็อป",
                    "vi", "máy tính xách tay",
                    "en", "laptop computer",
                    "fr", "ordinateur portable laptop computer",
                    "it", "computer portatile laptop")),
            ItemClass.of(3L, "충전기", ELECTRONICS, aliases(
                    "ja", "充電器",
                    "zh-Hans", "充电器",
                    "zh-Hant", "充電器",
                    "th", "ที่ชาร์จ",
                    "vi", "bộ sạc",
                    "en", "charger",
                    "fr", "chargeur charger",
                    "it", "caricabatterie charger")),
            ItemClass.of(4L, "보조배터리", ELECTRONICS, aliases(
                    "ja", "モバイルバッテリー リチウムイオン電池 予備電池",
                    "zh-Hans", "充电宝 移动电源 锂电池",
                    "zh-Hant", "行動電源 鋰電池",
                    "th", "พาวเวอร์แบงค์ แบตเตอรี่สำรอง",
                    "vi", "sạc dự phòng pin lithium",
                    "en", "power bank spare lithium battery",
                    "fr", "batterie externe lithium power bank spare battery",
                    "it", "power bank batteria al litio spare lithium battery")),
            ItemClass.of(5L, "이어폰", ELECTRONICS, aliases(
                    "ja", "イヤホン",
                    "zh-Hans", "耳机",
                    "zh-Hant", "耳機",
                    "th", "หูฟัง",
                    "vi", "tai nghe",
                    "en", "earphones",
                    "fr", "écouteurs earphones",
                    "it", "auricolari earphones")),
            ItemClass.of(6L, "상의", CLOTHING, aliases(
                    "ja", "衣類 上着",
                    "zh-Hans", "上衣 衣物",
                    "zh-Hant", "上衣 衣物",
                    "th", "เสื้อผ้า",
                    "vi", "quần áo",
                    "en", "clothing shirt",
                    "fr", "vêtements clothing",
                    "it", "abbigliamento clothing")),
            ItemClass.of(7L, "하의", CLOTHING, aliases(
                    "ja", "衣類 ズボン",
                    "zh-Hans", "裤子 衣物",
                    "zh-Hant", "褲子 衣物",
                    "th", "กางเกง",
                    "vi", "quần",
                    "en", "clothing trousers",
                    "fr", "pantalon trousers clothing",
                    "it", "pantaloni trousers clothing")),
            ItemClass.of(8L, "양말", CLOTHING, aliases(
                    "ja", "靴下",
                    "zh-Hans", "袜子",
                    "zh-Hant", "襪子",
                    "th", "ถุงเท้า",
                    "vi", "tất vớ",
                    "en", "socks",
                    "fr", "chaussettes socks",
                    "it", "calzini socks")),
            ItemClass.of(9L, "신발류", CLOTHING, aliases(
                    "ja", "靴",
                    "zh-Hans", "鞋子",
                    "zh-Hant", "鞋子",
                    "th", "รองเท้า",
                    "vi", "giày dép",
                    "en", "shoes",
                    "fr", "chaussures shoes",
                    "it", "scarpe shoes")),
            ItemClass.of(10L, "모자", CLOTHING, aliases(
                    "ja", "帽子",
                    "zh-Hans", "帽子",
                    "zh-Hant", "帽子",
                    "th", "หมวก",
                    "vi", "mũ nón",
                    "en", "hat",
                    "fr", "chapeau hat",
                    "it", "cappello hat")),
            ItemClass.of(11L, "선글라스", CLOTHING, aliases(
                    "ja", "サングラス",
                    "zh-Hans", "太阳镜 墨镜",
                    "zh-Hant", "太陽眼鏡",
                    "th", "แว่นกันแดด",
                    "vi", "kính râm",
                    "en", "sunglasses",
                    "fr", "lunettes de soleil sunglasses",
                    "it", "occhiali da sole sunglasses")),
            ItemClass.of(12L, "우산", ETC, aliases(
                    "ja", "傘",
                    "zh-Hans", "雨伞",
                    "zh-Hant", "雨傘",
                    "th", "ร่ม",
                    "vi", "ô dù",
                    "en", "umbrella",
                    "fr", "parapluie umbrella",
                    "it", "ombrello umbrella")),
            ItemClass.of(13L, "가위", ETC, aliases(
                    "ja", "はさみ 刃物",
                    "zh-Hans", "剪刀 利器",
                    "zh-Hant", "剪刀 利器",
                    "th", "กรรไกร ของมีคม",
                    "vi", "kéo vật sắc nhọn",
                    "en", "scissors sharp object",
                    "fr", "ciseaux scissors sharp object",
                    "it", "forbici scissors sharp object")),
            ItemClass.of(14L, "칼", ETC, aliases(
                    "ja", "刃物 ナイフ 凶器",
                    "zh-Hans", "刀具 利器 管制刀具",
                    "zh-Hant", "刀具 利器 管制刀械",
                    "th", "มีด ของมีคม",
                    "vi", "dao vật sắc nhọn",
                    "en", "knife blade sharp object",
                    "fr", "couteau lame knife blade sharp object",
                    "it", "coltello lama knife blade sharp object")),
            ItemClass.of(15L, "태블릿", ELECTRONICS, aliases(
                    "ja", "タブレット 電子機器",
                    "zh-Hans", "平板电脑 电子设备",
                    "zh-Hant", "平板電腦 電子設備",
                    "th", "แท็บเล็ต",
                    "vi", "máy tính bảng",
                    "en", "tablet",
                    "fr", "tablette tablet",
                    "it", "tablet")),
            ItemClass.of(16L, "삼각대", ELECTRONICS, aliases(
                    "ja", "三脚",
                    "zh-Hans", "三脚架",
                    "zh-Hant", "三腳架",
                    "th", "ขาตั้งกล้อง",
                    "vi", "chân máy ảnh",
                    "en", "tripod",
                    "fr", "trépied tripod",
                    "it", "treppiede tripod")),
            ItemClass.of(17L, "상비약", MEDICINE, aliases(
                    "ja", "医薬品類 常備薬",
                    "zh-Hans", "药品 常备药",
                    "zh-Hant", "藥品 常備藥",
                    "th", "ยา เวชภัณฑ์",
                    "vi", "thuốc men",
                    "en", "medication medicine",
                    "fr", "médicaments medication medicine",
                    "it", "medicinali medication medicine")),
            ItemClass.of(18L, "장갑", CLOTHING, aliases(
                    "ja", "手袋",
                    "zh-Hans", "手套",
                    "zh-Hant", "手套",
                    "th", "ถุงมือ",
                    "vi", "găng tay",
                    "en", "gloves",
                    "fr", "gants gloves",
                    "it", "guanti gloves")),
            ItemClass.of(19L, "칫솔", TOILETRIES, aliases(
                    "ja", "歯ブラシ",
                    "zh-Hans", "牙刷",
                    "zh-Hant", "牙刷",
                    "th", "แปรงสีฟัน",
                    "vi", "bàn chải đánh răng",
                    "en", "toothbrush",
                    "fr", "brosse à dents toothbrush",
                    "it", "spazzolino toothbrush")),
            ItemClass.of(20L, "치약", TOILETRIES, aliases(
                    "ja", "歯磨き粉 液体物 ジェル類",
                    "zh-Hans", "牙膏 液体 凝胶",
                    "zh-Hant", "牙膏 液體 凝膠",
                    "th", "ยาสีฟัน ของเหลว",
                    "vi", "kem đánh răng chất lỏng",
                    "en", "toothpaste liquid gel",
                    "fr", "dentifrice toothpaste liquid gel",
                    "it", "dentifricio toothpaste liquid gel")),
            ItemClass.of(21L, "텀블러", ETC, aliases(
                    "ja", "水筒 タンブラー 液体物",
                    "zh-Hans", "保温杯 水杯 液体",
                    "zh-Hant", "保溫杯 水杯 液體",
                    "th", "กระติกน้ำ ของเหลว",
                    "vi", "bình giữ nhiệt chất lỏng",
                    "en", "tumbler water bottle liquid",
                    "fr", "gourde bouteille water bottle liquid",
                    "it", "borraccia water bottle liquid")),
            ItemClass.of(22L, "백팩", ETC, aliases(
                    "ja", "バックパック リュック 手荷物",
                    "zh-Hans", "背包 手提行李",
                    "zh-Hant", "背包 手提行李",
                    "th", "เป้ กระเป๋าถือขึ้นเครื่อง",
                    "vi", "ba lô hành lý xách tay",
                    "en", "backpack carry-on bag",
                    "fr", "sac à dos backpack carry-on bag",
                    "it", "zaino backpack carry-on bag")));

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
