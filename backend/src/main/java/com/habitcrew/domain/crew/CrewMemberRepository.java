package com.habitcrew.domain.crew;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    Optional<CrewMember> findByCrewIdAndUserId(Long crewId, Long userId);

    boolean existsByCrewIdAndUserId(Long crewId, Long userId);

    long countByCrewId(Long crewId);

    long countByUserId(Long userId);

    /** 내가 참여한 크루 목록 (크루를 함께 가져와 N+1 쿼리를 막는다) */
    @Query("""
            select m from CrewMember m
            join fetch m.crew
            where m.user.id = :userId
            order by m.joinedAt desc
            """)
    List<CrewMember> findMyMemberships(@Param("userId") Long userId);

    /** 크루원 목록: 크루장 먼저, 그다음 먼저 들어온 순 */
    @Query("""
            select m from CrewMember m
            join fetch m.user
            where m.crew.id = :crewId
            order by case when m.role = com.habitcrew.domain.crew.CrewMember.Role.OWNER then 0 else 1 end, m.joinedAt
            """)
    List<CrewMember> findMembers(@Param("crewId") Long crewId);

    /** 여러 크루의 인원 수를 쿼리 한 번으로 센다: [crewId, count] */
    @Query("select m.crew.id, count(m) from CrewMember m where m.crew.id in :crewIds group by m.crew.id")
    List<Object[]> countByCrewIds(@Param("crewIds") Collection<Long> crewIds);
}
