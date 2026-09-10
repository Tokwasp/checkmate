package ssafy.checkmate.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여 "현재 로그인한 회원"을 주입받는 마커 애노테이션.
 *
 * 사용 예:
 *   public ResponseEntity<...> me(@LoginMember MemberPrincipal principal) { ... }
 *
 * 실제 값을 채우는 것은 LoginMemberArgumentResolver. 이 애노테이션 자체는 아무 로직이 없고,
 * 리졸버가 "이 파라미터는 내가 처리할 것"이라고 알아보는 표시 역할만 한다.
 * 필터를 거치지 않는 화이트리스트 경로(로그인 등)에서는 값이 없으므로 붙이면 안 된다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}
