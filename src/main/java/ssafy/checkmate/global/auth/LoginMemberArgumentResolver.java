package ssafy.checkmate.global.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginMember MemberPrincipal 파라미터에 값을 채워주는 리졸버.
 *
 * Spring MVC는 컨트롤러 메서드를 호출하기 전에 각 파라미터마다 "누가 이 값을 만들어줄까"를
 * 등록된 리졸버들에게 순서대로 물어본다(supportsParameter). true를 돌려준 리졸버의
 * resolveArgument 결과가 그 파라미터로 들어간다. @RequestBody, @PathVariable 도 같은 원리다.
 *
 * 여기서는 JwtAuthenticationFilter가 request attribute에 넣어둔 MemberPrincipal을 꺼내 돌려준다.
 * WebConfig에서 등록해야 동작한다.
 */
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    /** @LoginMember가 붙어 있고 타입이 MemberPrincipal인 파라미터만 처리한다 */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                && MemberPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object principal = webRequest.getAttribute(MemberPrincipal.ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
        if (principal == null) {
            // 필터를 거치지 않는 경로(화이트리스트, /api 밖)에 @LoginMember를 붙인 개발자 실수.
            // 인증 실패가 아니라 코드 버그이므로 401이 아닌 500으로 드러나게 한다.
            throw new IllegalStateException(
                    "MemberPrincipal not found in request. @LoginMember is used on a path that JwtAuthenticationFilter does not cover.");
        }
        return principal;
    }
}
