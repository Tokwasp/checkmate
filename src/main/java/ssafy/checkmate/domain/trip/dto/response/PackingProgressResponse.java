package ssafy.checkmate.domain.trip.dto.response;

import java.time.LocalDateTime;
import lombok.Getter;

/** 짐싸기 진척도 — sessionId/progress(%) 없이 "전체 중 몇 개 체크했는지"만 준다. */
@Getter
public class PackingProgressResponse {

    private final int packedItemCount;
    private final int totalItemCount;
    private final LocalDateTime lastUpdatedAt;

    private PackingProgressResponse(int packedItemCount, int totalItemCount, LocalDateTime lastUpdatedAt) {
        this.packedItemCount = packedItemCount;
        this.totalItemCount = totalItemCount;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public static PackingProgressResponse of(int packedItemCount, int totalItemCount, LocalDateTime lastUpdatedAt) {
        return new PackingProgressResponse(packedItemCount, totalItemCount, lastUpdatedAt);
    }
}
