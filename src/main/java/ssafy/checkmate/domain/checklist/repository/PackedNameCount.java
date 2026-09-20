package ssafy.checkmate.domain.checklist.repository;

/**
 * 회원이 담은 물품을 이름별로 센 한 줄 — {@link BagItemRepository#countPackedByMember} 의 결과.
 * 같은 이름이 여행마다 다른 분류로 담겼을 수 있어 분류는 그중 하나만 대표로 든다.
 */
public interface PackedNameCount {

    String getName();

    String getCategory();

    long getPackedCount();
}
