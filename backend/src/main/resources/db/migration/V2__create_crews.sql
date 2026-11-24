-- 크루: 같은 습관을 함께 인증하는 그룹
CREATE TABLE crews
(
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(30)  NOT NULL,
    description    VARCHAR(200),
    -- 인증 요일 비트마스크 (월=1, 화=2, 수=4, ... 일=64)
    cert_days      INTEGER      NOT NULL CHECK (cert_days BETWEEN 1 AND 127),
    -- 인증을 빼먹었을 때 쌓이는 벌금 포인트 (실제 돈이 아님)
    penalty_points INTEGER      NOT NULL CHECK (penalty_points BETWEEN 0 AND 10000),
    max_members    INTEGER      NOT NULL CHECK (max_members BETWEEN 2 AND 30),
    invite_code    VARCHAR(8)   NOT NULL,
    owner_id       BIGINT       NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT crews_invite_code_key UNIQUE (invite_code)
);

CREATE TABLE crew_members
(
    id        BIGSERIAL PRIMARY KEY,
    crew_id   BIGINT      NOT NULL REFERENCES crews (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users (id),
    role      VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crew_members_crew_user_key UNIQUE (crew_id, user_id)
);

-- 내 크루 목록 조회용 (crew_id 쪽은 유니크 제약의 인덱스를 쓴다)
CREATE INDEX idx_crew_members_user ON crew_members (user_id);
