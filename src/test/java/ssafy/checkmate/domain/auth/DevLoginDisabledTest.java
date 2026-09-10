package ssafy.checkmate.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * auth.dev-login.enabled=false 이면 DevAuthController 빈이 만들어지지 않아 엔드포인트 자체가 없어야 한다.
 * 운영 환경에서 개발용 로그인이 노출되지 않는다는 안전장치를 검증한다.
 *
 * properties 로 test 프로필의 값을 덮어쓰기 때문에 이 클래스는 별도의 애플리케이션 컨텍스트로 뜬다.
 */
@SpringBootTest(properties = "auth.dev-login.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevLoginDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("플래그가 꺼져 있으면 dev-login 엔드포인트는 존재하지 않는다(404)")
    void devLoginNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dev@test.com\"}"))
                .andExpect(status().isNotFound());
    }
}
