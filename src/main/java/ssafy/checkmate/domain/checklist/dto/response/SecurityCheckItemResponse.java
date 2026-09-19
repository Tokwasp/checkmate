package ssafy.checkmate.domain.checklist.dto.response;

import lombok.Getter;

@Getter
public class SecurityCheckItemResponse {

    private final Long itemId;
    private final String imageUrl;
    private final String name;
    private final String bagName;
    /** 금지 · 주의 · 통과 */
    private final String riskLevel;
    /** 통과 물품은 null. */
    private final String guide;

    private SecurityCheckItemResponse(Long itemId, String imageUrl, String name, String bagName,
            String riskLevel, String guide) {
        this.itemId = itemId;
        this.imageUrl = imageUrl;
        this.name = name;
        this.bagName = bagName;
        this.riskLevel = riskLevel;
        this.guide = guide;
    }

    public static SecurityCheckItemResponse of(Long itemId, String imageUrl, String name, String bagName,
            String riskLevel, String guide) {
        return new SecurityCheckItemResponse(itemId, imageUrl, name, bagName, riskLevel, guide);
    }
}
