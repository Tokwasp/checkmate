package ssafy.checkmate.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * 회원별 물건 마스터 — 마이페이지 「내 물건」 한 줄.
 *
 * 인벤토리 물품({@code BagItem})은 여행에 묶여 있어 "이 회원이 여권을 자주 담는다" 같은
 * 여행을 가로지르는 사실을 담을 곳이 없었다. 이 엔티티가 그 자리다. 회원 안에서 이름이 유일하다.
 *
 * 담은 횟수는 여기 두지 않는다 — 조회할 때 {@code BagItem} 에서 세면 되고, 그래야 물건을
 * 담는 기존 코드(체크리스트·스캔)를 한 줄도 건드리지 않는다. 여기에 남는 것은 회원이 직접 정한
 * 상태(즐겨찾기)와, 담은 적이 없어도 목록에 올라야 하는 물건의 존재다.
 *
 * 행은 두 경로로 생긴다. 즐겨찾기를 켤 때(아직 없으면 만든다), 그리고 목록을 조회할 때 담은
 * 기록에는 있는데 마스터에 없는 이름을 채워 넣을 때 — 모든 항목이 안정적인 id 를 가져야
 * 즐겨찾기 토글이 id 로 동작하기 때문이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "name"}))
public class MemberItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String name;

    /** 물품 분류. {@code BagItem.category} 와 같은 문자열을 쓴다 (예: "서류", "전자기기"). 모르면 null. */
    private String category;

    @Column(nullable = false)
    private boolean favorite;

    private MemberItem(Long memberId, String name, String category, boolean favorite) {
        this.memberId = memberId;
        this.name = name;
        this.category = category;
        this.favorite = favorite;
    }

    /** 담은 기록에서 발견된 물건 — 즐겨찾기는 꺼진 채로 시작한다. */
    public static MemberItem fromPacked(Long memberId, String name, String category) {
        return new MemberItem(memberId, name, category, false);
    }

    /** 회원이 즐겨찾기로 직접 올린 물건. */
    public static MemberItem asFavorite(Long memberId, String name, String category) {
        return new MemberItem(memberId, name, category, true);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    /** 분류를 모르던 물건이 나중에 분류가 붙은 채로 담기면 채운다. 이미 있으면 두던 대로 둔다. */
    public void fillCategoryIfEmpty(String category) {
        if (this.category == null && category != null) {
            this.category = category;
        }
    }
}
