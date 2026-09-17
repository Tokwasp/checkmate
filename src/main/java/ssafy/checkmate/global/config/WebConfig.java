package ssafy.checkmate.global.config;

import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ssafy.checkmate.global.auth.LoginMemberArgumentResolver;
import ssafy.checkmate.global.storage.LocalImageStorage;

/**
 * Spring MVC 설정. @LoginMember 리졸버 등록과, S3 설정이 없을 때 쓰는 로컬 업로드 파일 서빙을 한다.
 * 이후 CORS, 인터셉터 등 MVC 관련 설정도 여기에 추가한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginMemberArgumentResolver());
    }

    /**
     * LocalImageStorage가 저장한 파일을 내려주는 경로. S3를 쓰면 이 경로로는 아무것도 오지 않는다
     * (S3 URL을 직접 내려주기 때문). 인증 필터는 /api/* 에만 걸려 있어 이 경로는 공개다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageProperties.localDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(LocalImageStorage.URL_PREFIX + "/**")
                .addResourceLocations(location);
    }
}
