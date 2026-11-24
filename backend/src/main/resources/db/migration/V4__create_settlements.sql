-- 크루별·날짜별 정산 기록. (crew_id, settle_date)가 기본 키라서 같은 날을 두 번 정산할 수 없다
CREATE TABLE crew_settlements
(
    crew_id         BIGINT      NOT NULL REFERENCES crews (id) ON DELETE CASCADE,
    settle_date     DATE        NOT NULL,
    -- 정산 대상(그날 이전에 참여한 크루원) 수, 그중 인증한 수, 벌금을 받은 수
    target_count    INTEGER     NOT NULL DEFAULT 0,
    certified_count INTEGER     NOT NULL DEFAULT 0,
    penalized_count INTEGER     NOT NULL DEFAULT 0,
    settled_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (crew_id, settle_date)
);

-- 벌금: 인증일에 인증하지 않은 크루원에게 쌓이는 포인트 (실제 돈이 아님)
CREATE TABLE penalties
(
    id          BIGSERIAL PRIMARY KEY,
    crew_id     BIGINT      NOT NULL REFERENCES crews (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    settle_date DATE        NOT NULL,
    -- 정산 시점의 크루 벌금 (나중에 규칙이 바뀌어도 과거 기록은 그대로 남게 복사해 둔다)
    points      INTEGER     NOT NULL CHECK (points >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_penalties_daily UNIQUE (crew_id, user_id, settle_date)
);

-- 크루원별 벌금 합계
CREATE INDEX idx_penalties_crew_user ON penalties (crew_id, user_id);
