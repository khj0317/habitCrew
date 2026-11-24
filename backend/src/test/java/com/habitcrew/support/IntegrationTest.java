package com.habitcrew.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * 실제 PostgreSQL·Redis·SeaweedFS(S3) 컨테이너를 띄워서 테스트한다 (H2 같은 대체 DB와 달리 운영과 똑같이 동작).
 * 컨테이너는 테스트 클래스 사이에서 재사용된다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// 테스트 중에는 정산 스케줄러가 저절로 돌지 않게 끄고, 테스트에서 직접 부른다
@SpringBootTest(properties = "habitcrew.settlement.enabled=false")
@AutoConfigureMockMvc
@Import(IntegrationTest.Containers.class)
public @interface IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redis() {
            return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        }

        /** 로컬 개발과 같은 SeaweedFS·같은 접근 키 설정으로 띄운다 */
        @Bean
        GenericContainer<?> seaweedfs() {
            return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:latest"))
                    .withCopyFileToContainer(MountableFile.forHostPath("../infra/seaweedfs/s3.json"),
                            "/etc/seaweedfs/s3.json")
                    .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333",
                            "-s3.config=/etc/seaweedfs/s3.json", "-master.volumeSizeLimitMB=64", "-volume.max=4")
                    .withExposedPorts(8333)
                    .waitingFor(Wait.forHttp("/healthz").forPort(8333).withStartupTimeout(Duration.ofSeconds(90)));
        }

        @Bean
        DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfs) {
            return registry -> registry.add("habitcrew.storage.endpoint",
                    () -> "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333));
        }

        /** 테스트에서 날짜·시각을 옮길 수 있게 실제 시계 대신 쓴다 */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }
}
