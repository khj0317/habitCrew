package com.habitcrew.domain.record;

import com.habitcrew.domain.certification.CertificationEvent;
import com.habitcrew.domain.crew.CrewMember;
import com.habitcrew.domain.crew.CrewMemberRepository;
import com.habitcrew.domain.record.dto.RecordDtos;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 크루 월간 인증 랭킹. Redis Sorted Set(점수 = 그달 인증 횟수)에 둔다.
 *
 * <ul>
 *   <li><b>쓰기</b>: 인증이 커밋된 뒤 ZINCRBY로 1을 더하거나 뺀다. 원본은 DB(certifications)이고 Redis는 빠른 조회용 사본이다</li>
 *   <li><b>읽기</b>: ZREVRANGE로 정렬된 순서를 바로 꺼낸다 (DB에서 GROUP BY + ORDER BY 하지 않음)</li>
 *   <li><b>캐시가 없을 때</b>(처음, 만료, Redis 재시작): DB에서 다시 만들어 임시 키에 쓴 뒤 RENAME으로 한 번에 바꾼다.
 *       "준비됨" 표시가 없을 때는 쓰기를 건너뛰는데(Lua 스크립트로 확인과 증가를 원자적으로), 다시 만들 때 DB에서 어차피 세기 때문이다.
 *       이렇게 하지 않으면 빈 키에 1만 더해져 "1회짜리 랭킹"이 완성본처럼 보이게 된다.</li>
 *   <li><b>스스로 복구</b>: Redis 쓰기가 실패하면 "준비됨" 표시를 지워 다음 조회 때 DB에서 다시 만든다. 매일 정산 후에도 이번 달 표시를 지워
 *       하루에 한 번은 DB와 맞춘다 (드문 경쟁 상태로 생긴 차이도 이때 사라진다).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final Duration TTL = Duration.ofDays(45);

    /** 준비됨 표시가 있을 때만 점수를 바꾼다 (확인과 증가 사이에 다른 요청이 끼어들 수 없게 Lua로 한 번에) */
    private static final RedisScript<Long> INCREMENT_IF_READY = new DefaultRedisScript<>("""
            if redis.call('exists', KEYS[2]) == 1 then
              redis.call('zincrby', KEYS[1], ARGV[1], ARGV[2])
              redis.call('expire', KEYS[1], ARGV[3])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final NamedParameterJdbcTemplate jdbc;
    private final CrewMemberRepository memberRepository;
    private final ServiceTime serviceTime;

    static String key(Long crewId, YearMonth month) {
        return "rank:crew:" + crewId + ":" + month;
    }

    static String readyKey(Long crewId, YearMonth month) {
        return key(crewId, month) + ":ready";
    }

    @TransactionalEventListener
    public void onCertification(CertificationEvent event) {
        YearMonth month = YearMonth.from(event.certDate());
        int delta = event.type() == CertificationEvent.Type.CREATED ? 1 : -1;
        try {
            redis.execute(INCREMENT_IF_READY, List.of(key(event.crewId(), month), readyKey(event.crewId(), month)),
                    String.valueOf(delta), String.valueOf(event.userId()), String.valueOf(TTL.toSeconds()));
        } catch (RuntimeException e) {
            // 인증 자체는 이미 저장됐다. 랭킹만 다음 조회 때 DB에서 다시 만들도록 표시를 지운다
            log.warn("랭킹 갱신 실패, 다음 조회 때 다시 만듭니다: crew={}", event.crewId(), e);
            invalidate(List.of(event.crewId()), month);
        }
    }

    @Transactional(readOnly = true)
    public RecordDtos.Ranking ranking(Long userId, Long crewId, YearMonth month) {
        if (memberRepository.findByCrewIdAndUserId(crewId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.CREW_NOT_FOUND);
        }
        YearMonth target = month == null ? YearMonth.from(serviceTime.today()) : month;
        Map<Long, Integer> scores = scores(crewId, target);

        // 지금 크루원만, 인증 0회인 사람도 포함해서 보여 준다 (나간 사람의 점수는 빼고)
        List<CrewMember> members = memberRepository.findMembers(crewId);
        List<RecordDtos.RankingEntry> sorted = new ArrayList<>();
        members.stream()
                .sorted(Comparator.comparingInt((CrewMember m) -> scores.getOrDefault(m.getUser().getId(), 0)).reversed()
                        .thenComparing(CrewMember::getJoinedAt))
                .forEach(m -> sorted.add(new RecordDtos.RankingEntry(0, m.getUser().getId(), m.getUser().getNickname(),
                        scores.getOrDefault(m.getUser().getId(), 0))));

        // 공동 순위: 1, 2, 2, 4
        List<RecordDtos.RankingEntry> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RecordDtos.RankingEntry entry = sorted.get(i);
            int rank = i > 0 && sorted.get(i - 1).count() == entry.count() ? ranked.get(i - 1).rank() : i + 1;
            ranked.add(new RecordDtos.RankingEntry(rank, entry.userId(), entry.nickname(), entry.count()));
        }
        return new RecordDtos.Ranking(target, ranked);
    }

    /** Redis에서 점수를 읽는다. 준비되지 않았으면 DB에서 다시 만든다 */
    Map<Long, Integer> scores(Long crewId, YearMonth month) {
        String key = key(crewId, month);
        if (!Boolean.TRUE.equals(redis.hasKey(readyKey(crewId, month)))) {
            rebuild(crewId, month);
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, -1);
        Map<Long, Integer> scores = new HashMap<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> t : tuples) {
                if (t.getValue() != null && t.getScore() != null) {
                    scores.put(Long.valueOf(t.getValue()), t.getScore().intValue());
                }
            }
        }
        return scores;
    }

    /** DB 집계로 랭킹을 다시 만든다. 임시 키에 다 쓴 뒤 RENAME 하므로 읽는 쪽은 반쯤 만들어진 랭킹을 보지 않는다 */
    void rebuild(Long crewId, YearMonth month) {
        String key = key(crewId, month);
        String temp = key + ":build:" + UUID.randomUUID();
        Map<String, Double> counts = new HashMap<>();
        jdbc.query("""
                        select user_id, count(*) from certifications
                        where crew_id = :crewId and cert_date between :from and :to
                        group by user_id
                        """,
                new MapSqlParameterSource()
                        .addValue("crewId", crewId)
                        .addValue("from", month.atDay(1))
                        .addValue("to", month.atEndOfMonth()),
                rs -> {
                    counts.put(String.valueOf(rs.getLong(1)), (double) rs.getInt(2));
                });

        if (counts.isEmpty()) {
            redis.delete(key);
        } else {
            counts.forEach((member, score) -> redis.opsForZSet().add(temp, member, score));
            redis.rename(temp, key);
            redis.expire(key, TTL);
        }
        redis.opsForValue().set(readyKey(crewId, month), "1", TTL);
    }

    /** "준비됨" 표시를 지워 다음 조회 때 DB에서 다시 만들게 한다 */
    public void invalidate(Collection<Long> crewIds, YearMonth month) {
        try {
            redis.delete(crewIds.stream().map(id -> readyKey(id, month)).toList());
        } catch (RuntimeException e) {
            log.warn("랭킹 무효화 실패", e);
        }
    }
}
