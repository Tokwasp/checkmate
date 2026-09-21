package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ssafy.checkmate.domain.regulation.entity.RegulationLevel;

class QuarantineVerdictTest {

    @Test
    @DisplayName("금지·제한 판정은 사유·과태료·조치를 그대로 담는다")
    void ofProhibited() {
        // when
        QuarantineVerdict verdict = QuarantineVerdict.of(RegulationLevel.PROHIBITED,
                "육류 가공품은 검역 대상이에요.", "100만~1,000만 원의 과태료가 부과될 수 있어요.", "자진 신고하세요.");

        // then
        assertThat(verdict.level()).isEqualTo(RegulationLevel.PROHIBITED);
        assertThat(verdict.reason()).isEqualTo("육류 가공품은 검역 대상이에요.");
        assertThat(verdict.penalty()).isEqualTo("100만~1,000만 원의 과태료가 부과될 수 있어요.");
        assertThat(verdict.action()).isEqualTo("자진 신고하세요.");
    }

    @Test
    @DisplayName("허용 판정이면 모델이 과태료와 조치를 줘도 버린다")
    void ofAllowedDropsPenaltyAndAction() {
        // when
        QuarantineVerdict verdict = QuarantineVerdict.of(RegulationLevel.ALLOWED,
                "검역 대상이 아니에요.", "과태료가 있어요.", "신고하세요.");

        // then
        assertThat(verdict.reason()).isEqualTo("검역 대상이 아니에요.");
        assertThat(verdict.penalty()).isNull();
        assertThat(verdict.action()).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 없는 값으로 보고 null로 정리한다")
    void ofBlankToNull() {
        // when
        QuarantineVerdict verdict = QuarantineVerdict.of(RegulationLevel.RESTRICTED, "사유", "  ", "");

        // then
        assertThat(verdict.penalty()).isNull();
        assertThat(verdict.action()).isNull();
    }
}
