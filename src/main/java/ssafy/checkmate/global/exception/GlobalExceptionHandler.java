package ssafy.checkmate.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ssafy.checkmate.global.response.ApiResponse;

/**
 * 모든 응답 예외가 지나가는 지점. EC2에서 "어느 요청이 왜 몇 번으로 끝났는지"를 한 줄로
 * 따라갈 수 있도록 {@code [exception]} 접두사와 함께 메서드·경로·상태를 남긴다.
 *
 * 레벨 기준(운영 로그 레벨이 INFO라 그 아래로는 내리지 않는다):
 * - 4xx 중 의도된 비즈니스 실패는 info
 * - 잘못된 요청/없는 경로처럼 클라이언트를 고쳐야 하는 건 warn
 * - 5xx는 스택 트레이스까지 error
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        HttpStatus status = e.getErrorCode().getStatus();
        log.info("[exception] 비즈니스 예외 {} {} -> {} {} ({})",
                request.getMethod(), request.getRequestURI(), status.value(), e.getMessage(),
                e.getClass().getSimpleName());
        return ResponseEntity.status(status).body(ApiResponse.error(status, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("잘못된 요청입니다.");
        log.warn("[exception] 요청 검증 실패 {} {} -> 400 {} / fields={}",
                request.getMethod(), request.getRequestURI(), message, fieldErrorsOf(e));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler({ResponseStatusException.class, ErrorResponseException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleErrorResponseException(ErrorResponse e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getBody().getDetail();
        log.warn("[exception] {} {} -> {} {} ({})",
                request.getMethod(), request.getRequestURI(), status.value(),
                message != null ? message : status.getReasonPhrase(), e.getClass().getSimpleName());
        return ResponseEntity.status(status).body(ApiResponse.error(status, message != null ? message : status.getReasonPhrase()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException e, HttpServletRequest request) {
        log.error("[exception] DB 접근 실패 {} {} -> 500", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("[exception] 처리하지 못한 예외 {} {} -> 500", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
    }

    /** 어느 필드가 걸렸는지까지 한 줄로 남긴다 — 값은 담지 않는다(개인정보/토큰이 로그에 남지 않도록). */
    private String fieldErrorsOf(MethodArgumentNotValidException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "=" + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
    }
}
