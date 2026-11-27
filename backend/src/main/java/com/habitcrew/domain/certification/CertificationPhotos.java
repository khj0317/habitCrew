package com.habitcrew.domain.certification;

import com.habitcrew.global.storage.PhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 인증 사진 파일 지우기를 한곳에서 처리한다.
 * - DB에서 지운 것이 확정(커밋)된 뒤에만 파일을 지운다. 롤백됐는데 사진만 사라지는 일이 없게
 * - 체험 크루가 같이 쓰는 샘플 사진(samples/)은 지우지 않는다
 */
@Component
@RequiredArgsConstructor
public class CertificationPhotos {

    public static final String SAMPLE_PREFIX = "samples/";

    private final PhotoStorage storage;
    private final JdbcTemplate jdbc;

    /** 이 크루의 모든 인증 사진 경로 (크루를 지우기 전에 모아 둔다) */
    public List<String> keysOfCrew(Long crewId) {
        List<String> keys = new ArrayList<>();
        jdbc.query("select image_key, thumbnail_key from certifications where crew_id = ?", rs -> {
            keys.add(rs.getString(1));
            keys.add(rs.getString(2));
        }, crewId);
        return keys;
    }

    /** 지금 트랜잭션이 커밋되면 지운다. 트랜잭션 밖이면 바로 지운다 */
    public void deleteAfterCommit(Collection<String> keys) {
        List<String> targets = keys.stream().filter(k -> !k.startsWith(SAMPLE_PREFIX)).toList();
        if (targets.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    targets.forEach(storage::delete);
                }
            });
        } else {
            targets.forEach(storage::delete);
        }
    }
}
