package ssafy.checkmate.domain.checklist.service;

/** GMS 응답 본문을 파싱하기 전에 거치는 손질 — 여러 GMS 클라이언트가 같이 쓴다. */
final class GmsResponses {

    private GmsResponses() {
    }

    /** 모델이 지시를 어기고 ```json 으로 감싸는 경우가 있어 벗겨서 파싱한다. */
    static String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\n?", "");
            trimmed = trimmed.replaceFirst("```\s*$", "");
        }
        return trimmed.trim();
    }
}
