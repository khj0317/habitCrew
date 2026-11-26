package com.habitcrew.domain.settlement;

import com.habitcrew.domain.record.RankingService;
import com.habitcrew.global.time.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 밀린 정산을 모두 처리한다 (매일 00:05, 그리고 서버가 뜰 때).
 *
 * <ul>
 *   <li><b>따라잡기</b>: 서버가 꺼져 있어 자정을 놓쳤어도, 다음에 돌 때 최근 N일 중 정산 안 된 날을 모두 처리한다</li>
 *   <li><b>서버가 여러 대일 때</b>: PostgreSQL 어드바이저리 락으로 한 대만 돌게 한다. 락을 못 잡은 서버는 그냥 넘어간다.
 *       (락이 없어도 정산 기록의 기본 키 덕분에 벌금이 두 번 매겨지지는 않지만, 같은 일을 여러 서버가 하지 않게 한다)</li>
 *   <li><b>크루별 격리</b>: 크루마다 트랜잭션을 따로 써서, 한 크루에서 오류가 나도 다른 크루 정산은 계속된다</li>
 * </ul>
 */
@Slf4j
@Component
public class SettlementRunner {

    /** 어드바이저리 락 번호 (이 앱 안에서 정산 작업을 뜻하는 임의의 고정 값) */
    static final long LOCK_KEY = 4_820_001L;

    private final SettlementService settlementService;
    private final RankingService rankingService;
    private final ServiceTime serviceTime;
    private final DataSource dataSource;
    private final int catchUpDays;

    public SettlementRunner(SettlementService settlementService, RankingService rankingService, ServiceTime serviceTime,
                            DataSource dataSource, @Value("${habitcrew.settlement.catch-up-days}") int catchUpDays) {
        this.settlementService = settlementService;
        this.rankingService = rankingService;
        this.serviceTime = serviceTime;
        this.dataSource = dataSource;
        this.catchUpDays = catchUpDays;
    }

    public record Result(boolean ran, int settledCount) {
    }

    /** 최근 N일(어제까지) 중 정산하지 않은 날을 오래된 날부터 처리한다 */
    public Result runPending() {
        LocalDate today = serviceTime.today();
        return withLock(() -> {
            int settled = 0;
            for (LocalDate date = today.minusDays(catchUpDays); date.isBefore(today); date = date.plusDays(1)) {
                settled += settleDate(date);
            }
            // 하루에 한 번은 랭킹 캐시를 DB와 맞춘다 (드물게 생길 수 있는 차이를 스스로 고친다)
            rankingService.invalidate(settlementService.allCrewIds(), YearMonth.from(today));
            return settled;
        });
    }

    /** 특정 날짜 하나만 정산한다 (로컬 확인용) */
    public Result runFor(LocalDate date) {
        return withLock(() -> settleDate(date));
    }

    private int settleDate(LocalDate date) {
        int settled = 0;
        for (Long crewId : settlementService.crewsToSettle(date)) {
            try {
                if (settlementService.settle(crewId, date)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                // 이 크루만 다음 실행 때 다시 시도된다 (정산 기록이 롤백돼 "안 한 날"로 남으므로)
                log.error("정산 실패: crew={} date={}", crewId, date, e);
            }
        }
        return settled;
    }

    private interface Work {
        int run();
    }

    private Result withLock(Work work) {
        // 세션 단위 락이라 작업이 끝날 때까지 이 연결을 붙잡고, 끝나면 풀고 돌려준다
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                log.info("다른 서버가 정산 중이라 이번에는 건너뜁니다.");
                return new Result(false, 0);
            }
            try {
                return new Result(true, work.run());
            } finally {
                unlock(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("정산 락을 다루지 못했습니다.", e);
        }
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, LOCK_KEY);
            ps.execute();
        }
    }
}
