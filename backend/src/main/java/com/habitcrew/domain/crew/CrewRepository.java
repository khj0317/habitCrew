package com.habitcrew.domain.crew;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CrewRepository extends JpaRepository<Crew, Long> {

    Optional<Crew> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /**
     * 크루 행에 쓰기 잠금을 건다 (SELECT ... FOR UPDATE).
     * 정원이 1자리 남았을 때 두 명이 동시에 참여해도, 한 명씩 차례로 인원을 세게 만들어 정원 초과를 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.id = :id")
    Optional<Crew> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.inviteCode = :inviteCode")
    Optional<Crew> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);
}
