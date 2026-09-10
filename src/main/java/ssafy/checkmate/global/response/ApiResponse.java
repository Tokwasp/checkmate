package ssafy.checkmate.global.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponse<T> {

    private final int status;
    private final String message;
    private final T data;

    private ApiResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), "성공", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(HttpStatus.OK.value(), "성공", null);
    }

    public static <T> ApiResponse<T> accepted(T data) {
        return new ApiResponse<>(HttpStatus.ACCEPTED.value(), "성공", data);
    }

    /** 실패 응답. data는 항상 null. JwtAuthenticationFilter가 401 본문을 만들 때 사용한다. */
    public static <T> ApiResponse<T> error(HttpStatus status, String message) {
        return new ApiResponse<>(status.value(), message, null);
    }
}
