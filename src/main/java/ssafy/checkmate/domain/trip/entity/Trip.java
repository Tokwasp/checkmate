package ssafy.checkmate.domain.trip.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ssafy.checkmate.global.entity.BaseEntity;

/**
 * Trip 도메인 본체가 만들어지기 전까지의 더미 엔티티.
 * checklist/bag 도메인이 tripId로 조회하는 최소 정보만 가진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 여행 목적(캠핑, 수영 등) 여러 개를 가질 수 있다 — 체크리스트 추천이 이 값을 참고한다. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trip_purpose", joinColumns = @JoinColumn(name = "trip_id"))
    @Column(name = "purpose")
    private List<String> purposes = new ArrayList<>();

    @Builder
    public Trip(Long memberId, String destination, LocalDate startDate, LocalDate endDate, List<String> purposes) {
        this.memberId = memberId;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.purposes = purposes != null ? purposes : new ArrayList<>();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
