package com.habitcrew.global.storage;

/**
 * 사진 저장소. 구현을 바꿔 끼울 수 있게 인터페이스로 둔다 (S3 호환 저장소, 테스트용 메모리 저장소).
 */
public interface PhotoStorage {

    void put(String key, byte[] content, String contentType);

    /** 일정 시간 동안만 쓸 수 있는 서명된 주소. 저장소가 공개되어 있지 않아도 이 주소로는 볼 수 있다 */
    String url(String key);

    void delete(String key);
}
