package ssafy.checkmate.domain.checklist.budget;

import org.springframework.http.HttpStatus;
import ssafy.checkmate.global.exception.BusinessException;
import ssafy.checkmate.global.exception.ErrorCode;

public class RagBudgetException extends BusinessException {

    private RagBudgetException(Status status) {
        super(status);
    }

    public static RagBudgetException exhausted() {
        return new RagBudgetException(Status.EXHAUSTED);
    }

    public static RagBudgetException inputTooLarge() {
        return new RagBudgetException(Status.INPUT_TOO_LARGE);
    }

    public static RagBudgetException unavailable() {
        return new RagBudgetException(Status.UNAVAILABLE);
    }

    private enum Status implements ErrorCode {
        EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "오늘의 규정 검사 예산을 소진했습니다."),
        INPUT_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT, "한 번에 검사할 수 있는 규정 분량을 초과했습니다."),
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "규정 검사 비용을 확인할 수 없습니다.");

        private final HttpStatus status;
        private final String message;

        Status(HttpStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
