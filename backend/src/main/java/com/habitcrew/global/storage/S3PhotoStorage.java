package com.habitcrew.global.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 호환 저장소에 사진을 둔다.
 * 버킷은 공개하지 않고, 크루원이 피드를 볼 때마다 서버가 짧게 유효한 서명 URL을 만들어 준다.
 * 그래서 사진 주소가 밖으로 새어 나가도 얼마 지나지 않아 열 수 없게 된다.
 */
@Slf4j
public class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3PhotoStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        // 같은 키에 다른 사진이 올라가는 일이 없으므로 브라우저가 오래 캐시해도 된다
                        .cacheControl("private, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public String url(String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.urlTtl())
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            // 지우지 못한 파일은 저장 공간만 차지할 뿐 서비스에는 영향이 없다: 기록만 남긴다
            log.warn("사진을 지우지 못했습니다: {}", key, e);
        }
    }
}
