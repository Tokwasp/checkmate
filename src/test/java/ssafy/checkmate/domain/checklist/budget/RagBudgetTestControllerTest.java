package ssafy.checkmate.domain.checklist.budget;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ssafy.checkmate.domain.checklist.budget.RagBudgetTest.CLOCK;
import static ssafy.checkmate.domain.checklist.budget.RagBudgetTest.LIMIT;
import static ssafy.checkmate.domain.checklist.budget.RagBudgetTest.MODEL;
import static ssafy.checkmate.domain.checklist.budget.RagBudgetTest.TEST_KEY;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ssafy.checkmate.global.config.BedrockProperties;
import ssafy.checkmate.global.exception.GlobalExceptionHandler;

class RagBudgetTestControllerTest {

    private static final String PATH = "/internal/test-api/rag-budget/";
    private static final String HEADER = "X-Budget-Test-Key";
    private final RagBudgetProperties properties = RagBudgetTest.properties(true);
    private final UpstashRagBudgetStore store = mock(UpstashRagBudgetStore.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RagBudgetTestController(
                    new RagBudget(properties, store, mock(RagTokenCounter.class), CLOCK),
                    new BedrockProperties("ap-northeast-2", "", MODEL, 15, 4_000), properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void reportsBothTestScenariosOnSameRedisKey() throws Exception {
        given(store.settle(eq(TEST_KEY), anyString(), eq(30_469_500L))).willReturn(LIMIT - 30_469_500L);
        given(store.settle(eq(TEST_KEY), anyString(), eq(24_000_000L))).willReturn(LIMIT - 54_469_500L);

        mvc.perform(post(PATH + "quarantine").header(HEADER, properties.testApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redisKey").value(TEST_KEY))
                .andExpect(jsonPath("$.data.chargedKrw").value(30.4695))
                .andExpect(jsonPath("$.data.remainingKrw").value(9969.5305));
        mvc.perform(post(PATH + "security").header(HEADER, properties.testApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redisKey").value(TEST_KEY))
                .andExpect(jsonPath("$.data.chargedKrw").value(24))
                .andExpect(jsonPath("$.data.remainingKrw").value(9945.5305));
    }

    @Test
    void returns429WhenRedisDeniesReservation() throws Exception {
        given(store.reserve(eq(TEST_KEY), anyString(), anyLong(), eq(LIMIT), anyLong())).willReturn(-1L);
        mvc.perform(post(PATH + "quarantine").header(HEADER, properties.testApiKey()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requiresKeyBeforeAccessingRedis() throws Exception {
        mvc.perform(post(PATH + "security"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(store);
    }
}
