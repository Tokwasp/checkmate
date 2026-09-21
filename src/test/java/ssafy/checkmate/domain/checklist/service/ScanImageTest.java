package ssafy.checkmate.domain.checklist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ssafy.checkmate.domain.checklist.exception.InvalidScanImageException;
import ssafy.checkmate.domain.checklist.exception.UnsupportedScanImageException;

class ScanImageTest {

    @Test
    @DisplayName("JPEG·PNG·WebP는 바이트와 형식을 그대로 담아 만든다")
    void from() {
        // given
        byte[] bytes = {1, 2, 3};

        // when
        ScanImage jpeg = ScanImage.from(file("image/jpeg", bytes));
        ScanImage png = ScanImage.from(file("image/png", bytes));
        ScanImage webp = ScanImage.from(file("image/webp", bytes));

        // then
        assertThat(jpeg.bytes()).containsExactly(1, 2, 3);
        assertThat(jpeg.mimeType()).isEqualTo("image/jpeg");
        assertThat(png.mimeType()).isEqualTo("image/png");
        assertThat(webp.mimeType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("형식은 대소문자를 가리지 않고 소문자로 정리한다")
    void fromWithUpperCaseMimeType() {
        // when
        ScanImage image = ScanImage.from(file("IMAGE/JPEG", new byte[] {1}));

        // then
        assertThat(image.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("이미지가 없으면 26번과 같은 '유효하지 않은 이미지' 예외가 발생한다")
    void fromWithoutFile() {
        // when // then
        assertThatThrownBy(() -> ScanImage.from(null))
                .isInstanceOf(InvalidScanImageException.class);
    }

    @Test
    @DisplayName("빈 파일이면 '유효하지 않은 이미지' 예외가 발생한다")
    void fromWithEmptyFile() {
        // when // then
        assertThatThrownBy(() -> ScanImage.from(file("image/jpeg", new byte[0])))
                .isInstanceOf(InvalidScanImageException.class);
    }

    @Test
    @DisplayName("JPEG·PNG·WebP가 아닌 형식은 예외가 발생한다")
    void fromWithUnsupportedMimeType() {
        // when // then
        assertThatThrownBy(() -> ScanImage.from(file("image/gif", new byte[] {1})))
                .isInstanceOf(UnsupportedScanImageException.class)
                .hasMessage("이미지는 5MB 이하의 JPEG, PNG, WebP만 올릴 수 있습니다.");
    }

    @Test
    @DisplayName("형식을 알 수 없으면 예외가 발생한다")
    void fromWithoutContentType() {
        // when // then
        assertThatThrownBy(() -> ScanImage.from(new MockMultipartFile("image", "a.jpg", null, new byte[] {1})))
                .isInstanceOf(UnsupportedScanImageException.class);
    }

    @Test
    @DisplayName("5MB를 넘으면 예외가 발생하고, 정확히 5MB는 통과한다")
    void fromWithSizeBoundary() {
        // given
        byte[] limit = new byte[(int) ScanImage.MAX_BYTES];
        byte[] over = new byte[(int) ScanImage.MAX_BYTES + 1];

        // when // then
        assertThat(ScanImage.from(file("image/png", limit)).bytes()).hasSize(limit.length);
        assertThatThrownBy(() -> ScanImage.from(file("image/png", over)))
                .isInstanceOf(UnsupportedScanImageException.class);
    }

    private MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("image", "photo", contentType, bytes);
    }
}
