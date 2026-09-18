package ssafy.checkmate.global.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 이미지를 저장하고 접근 URL을 돌려주는 저장소.
 * 운영에서는 S3({@link S3ImageStorage}), 저장소 설정이 없는 로컬/테스트에서는
 * {@link LocalImageStorage}가 쓰인다 — {@link ssafy.checkmate.global.config.StorageConfig} 참고.
 */
public interface ImageStorage {

    /**
     * @param image     저장할 이미지
     * @param directory 저장 경로 접두사 (예: 회원 ID — 회원별로 사진을 구분해 볼 수 있도록)
     * @return 저장된 이미지 접근 URL
     */
    String upload(MultipartFile image, String directory);
}
