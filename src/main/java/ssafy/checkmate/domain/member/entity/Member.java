package ssafy.checkmate.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String nickname;

    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * 반입 규정 검사(30번)를 호출한 누적 횟수. 계정당 상한을 두어 Bedrock 비용이 무한정
     * 늘어나는 것을 막는다. 상한 값과 증가 시점은
     * {@code SecurityCheckService}가 관리하고, 증가는 조건부 UPDATE 한 문장으로 원자적으로
     * 처리한다({@code MemberRepository#consumeSecurityCheckQuota}) — 조회 후 증가로 나누면
     * 동시 요청 두 개가 같은 횟수를 읽고 둘 다 통과한다.
     */
    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int securityCheckCount = 0;

    /**
     * 촬영 물품 반입 판정(35번)을 호출한 누적 횟수. 30번의 {@link #securityCheckCount}와 <b>따로</b>
     * 센다 — 규정 검사를 다 써도 물품 분석이 막히지 않게 하기 위해서다. 상한과 증가 시점은
     * {@code QuarantineCheckService}가 관리하고, 증가는 조건부 UPDATE 한 문장으로 처리한다
     * ({@code MemberRepository#consumeItemAnalysisQuota}).
     */
    @Column(nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private int itemAnalysisCount = 0;

    @Builder
    public Member(String email, String nickname, String profileImageUrl, Provider provider) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.provider = provider;
        this.role = Role.USER;
        this.securityCheckCount = 0;
        this.itemAnalysisCount = 0;
    }

    /** 프로필 사진 URL 을 바꾼다. null 이면 기본 아이콘으로 돌아간다 (마이페이지 사진 삭제). */
    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
