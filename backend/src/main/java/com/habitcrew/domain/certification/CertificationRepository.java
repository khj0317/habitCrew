package com.habitcrew.domain.certification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByCrewIdAndUserIdAndCertDate(Long crewId, Long userId, LocalDate certDate);

    /**
     * 크루 피드 (최신순). OFFSET 대신 "마지막으로 본 id보다 작은 것"으로 다음 페이지를 가져온다.
     * 새 인증이 올라와도 다음 페이지에서 같은 글이 두 번 나오지 않고, 뒤 페이지로 갈수록 느려지지도 않는다.
     */
    @Query("""
            select c from Certification c
            join fetch c.user
            where c.crewId = :crewId and (:cursor is null or c.id < :cursor)
            order by c.id desc
            """)
    List<Certification> findFeed(@Param("crewId") Long crewId, @Param("cursor") Long cursor, Limit limit);

    @Query("select c from Certification c join fetch c.user where c.id = :id")
    Optional<Certification> findWithUser(@Param("id") Long id);

    List<Certification> findByCrewIdAndCertDate(Long crewId, LocalDate certDate);

    // ---- 좋아요: 같은 사람이 여러 번 눌러도 한 번만 반영되게 INSERT ... ON CONFLICT DO NOTHING 을 쓴다 ----

    @Modifying
    @Query(value = """
            insert into certification_likes (certification_id, user_id) values (:id, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLike(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying
    @Query(value = "delete from certification_likes where certification_id = :id and user_id = :userId",
            nativeQuery = true)
    int deleteLike(@Param("id") Long id, @Param("userId") Long userId);

    @Query(value = """
            select certification_id from certification_likes
            where user_id = :userId and certification_id in (:ids)
            """, nativeQuery = true)
    List<Long> findLikedIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Query(value = "select exists(select 1 from certification_likes where certification_id = :id and user_id = :userId)",
            nativeQuery = true)
    boolean isLiked(@Param("id") Long id, @Param("userId") Long userId);

    // ---- 개수 갱신: "읽고 +1 해서 쓰기"는 동시에 누르면 숫자가 틀어지므로 DB에서 바로 더한다 ----

    @Modifying
    @Query(value = "update certifications set like_count = like_count + :delta where id = :id", nativeQuery = true)
    void addLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying
    @Query(value = "update certifications set comment_count = comment_count + :delta where id = :id", nativeQuery = true)
    void addCommentCount(@Param("id") Long id, @Param("delta") int delta);

    @Query(value = "select like_count from certifications where id = :id", nativeQuery = true)
    int likeCount(@Param("id") Long id);

    // ---- 신고 ----

    @Modifying
    @Query(value = """
            insert into certification_reports (certification_id, user_id, reason) values (:id, :userId, :reason)
            on conflict do nothing
            """, nativeQuery = true)
    int insertReport(@Param("id") Long id, @Param("userId") Long userId, @Param("reason") String reason);

    @Query(value = "select count(*) from certification_reports where certification_id = :id", nativeQuery = true)
    int reportCount(@Param("id") Long id);

    @Query(value = """
            select certification_id from certification_reports
            where user_id = :userId and certification_id in (:ids)
            """, nativeQuery = true)
    List<Long> findReportedIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Modifying
    @Query(value = "update certifications set hidden = :hidden where id = :id", nativeQuery = true)
    void setHidden(@Param("id") Long id, @Param("hidden") boolean hidden);
}
