package ssafy.checkmate.global.storage;

/** 업로드 파일 이름 처리 유틸. 확장자만 원본에서 가져오고 이름은 UUID로 새로 만든다. */
final class StorageFileName {

    private StorageFileName() {
    }

    /** 원본 파일명에서 확장자를 뽑는다. 확장자가 없으면 빈 문자열. */
    static String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            return "";
        }
        return originalFileName.substring(dotIndex).toLowerCase();
    }
}
