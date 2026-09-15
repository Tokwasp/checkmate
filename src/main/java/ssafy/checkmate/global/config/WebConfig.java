package ssafy.checkmate.global.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ssafy.checkmate.global.auth.LoginMemberArgumentResolver;

/**
 * Spring MVC 설정. 현재는 @LoginMember 리졸버 등록만 한다.
 * 이후 CORS, 인터셉터 등 MVC 관련 설정도 여기에 추가한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginMemberArgumentResolver());
    }
}
