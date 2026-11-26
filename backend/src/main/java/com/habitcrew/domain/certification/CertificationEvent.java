package com.habitcrew.domain.certification;

import java.time.LocalDate;

/**
 * 인증이 생기거나 지워졌을 때 알린다. 랭킹처럼 인증 자체와 따로 관리되는 데이터가 받아서 갱신한다.
 * 트랜잭션이 커밋된 뒤에만 처리되므로(@TransactionalEventListener), 롤백된 인증이 랭킹에 반영되는 일은 없다.
 */
public record CertificationEvent(Type type, Long crewId, Long userId, LocalDate certDate) {

    public enum Type { CREATED, DELETED }
}
