package com.habitcrew;

import com.habitcrew.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 실행과 같은 설정(정산·알림·체험 계정 정리 스케줄러 모두 켬)으로 서버가 뜨는지 확인한다.
 * 다른 통합 테스트는 스케줄러를 꺼 두기 때문에, 스케줄러가 켜질 때만 생기는 빈 충돌 같은 문제를 여기서 잡는다
 * (실제로 TaskExecutor 빈이 두 개가 되어 서버가 뜨지 않던 문제를 이 테스트로 막는다).
 */
@SpringBootTest(properties = {"habitcrew.settlement.enabled=true", "habitcrew.push.reminder-enabled=true",
        "habitcrew.guest.cleanup-enabled=true"})
@Import(IntegrationTest.Containers.class)
class ApplicationStartupTest {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("스케줄러를 모두 켠 실제 설정으로 서버가 뜬다")
    void startsWithSchedulers() {
        assertThat(context.getBeanNamesForType(org.springframework.scheduling.TaskScheduler.class)).isNotEmpty();
    }
}
