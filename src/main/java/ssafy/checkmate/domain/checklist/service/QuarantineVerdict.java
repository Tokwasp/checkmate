package ssafy.checkmate.domain.checklist.service;

import ssafy.checkmate.domain.regulation.entity.RegulationLevel;

/**
 * 한국 반입 규정에 비춘 판정(35번 3단계). 판정 단계는 귀국 규정 안내(34번)와 같은
 * {@link RegulationLevel}(금지·제한·허용)을 쓴다.
 *
 * 모델이 준 값을 그대로 믿지 않고 여기서 정리한다 — 빈 문자열은 없는 값으로 보고, 허용이면 과태료와
 * 조치는 있어서는 안 되므로 버린다("허용인데 과태료 안내"는 화면에서 모순으로 보인다).
 */
public record QuarantineVerdict(RegulationLevel level, String reason, String penalty, String action) {

    public static QuarantineVerdict of(RegulationLevel level, String reason, String penalty, String action) {
        if (level == RegulationLevel.ALLOWED) {
            return new QuarantineVerdict(level, blankToNull(reason), null, null);
        }
        return new QuarantineVerdict(level, blankToNull(reason), blankToNull(penalty), blankToNull(action));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
