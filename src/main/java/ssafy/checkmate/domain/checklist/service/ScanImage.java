package ssafy.checkmate.domain.checklist.service;

import java.io.IOException;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import ssafy.checkmate.domain.checklist.exception.InvalidScanImageException;
import ssafy.checkmate.domain.checklist.exception.UnsupportedScanImageException;

/**
 * 촬영 물품 반입 판정(35번)이 받는 사진. 저장하지 않고 메모리에서 GMS로 보내고 버리므로 바이트를
 * 그대로 들고 다닌다.
 *
 * 허용 형식과 크기 상한이 이 객체의 규칙이다 — 서버 멀티파트 상한(10MB)보다 작게 잡는 이유는,
 * GMS로 보낼 때 base64로 인코딩되면 약 4/3배가 되고 비전 모델은 이미지 한 장당 용량 상한이 있기
 * 때문이다. 앱이 올리기 전에 줄여서 보내는 것을 전제로 한다.
 */
public record ScanImage(byte[] bytes, String mimeType) {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** 비었으면 26번과 같은 "유효하지 않다", 형식·크기 위반이면 "지원하지 않는 형식/크기"로 거절한다. */
    public static ScanImage from(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidScanImageException();
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new UnsupportedScanImageException();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new UnsupportedScanImageException();
        }
        try {
            return new ScanImage(file.getBytes(), mimeType.toLowerCase());
        } catch (IOException e) {
            throw new InvalidScanImageException();
        }
    }
}
