package com.habitcrew.domain.certification;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CertificationCommentRepository extends JpaRepository<CertificationComment, Long> {

    @Query("""
            select c from CertificationComment c
            join fetch c.user
            where c.certificationId = :certificationId
            order by c.id
            """)
    List<CertificationComment> findByCertification(@Param("certificationId") Long certificationId, Limit limit);
}
