package com.habitcrew.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Slf4j
@Configuration
public class StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                // SeaweedFS·MinIO처럼 버킷 이름을 도메인이 아닌 경로로 쓰는 저장소와 호환
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public PhotoStorage photoStorage(S3Client s3Client, S3Presigner s3Presigner, StorageProperties properties) {
        return new S3PhotoStorage(s3Client, s3Presigner, properties);
    }

    /** 로컬 개발 편의: 버킷이 없으면 만든다 */
    @Bean
    public ApplicationRunner createBucketIfMissing(S3Client s3Client, StorageProperties properties) {
        return args -> {
            if (!properties.createBucket()) {
                return;
            }
            try {
                s3Client.headBucket(b -> b.bucket(properties.bucket()));
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(b -> b.bucket(properties.bucket()));
                log.info("사진 버킷을 만들었습니다: {}", properties.bucket());
            }
        };
    }

    private static StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
