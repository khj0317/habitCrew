-- 인증: 크루원이 인증일에 올린 사진 한 장
CREATE TABLE certifications
(
    id            BIGSERIAL PRIMARY KEY,
    crew_id       BIGINT       NOT NULL REFERENCES crews (id) ON DELETE CASCADE,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    -- 서비스 기준 시간대(KST)의 날짜. 요청이 도착한 순간의 날짜로 정한다 (업로드가 늦어져도 자정을 넘기지 않게)
    cert_date     DATE         NOT NULL,
    message       VARCHAR(200),
    image_key     VARCHAR(200) NOT NULL,
    thumbnail_key VARCHAR(200) NOT NULL,
    image_width   INTEGER      NOT NULL,
    image_height  INTEGER      NOT NULL,
    -- 피드를 그릴 때마다 COUNT 하지 않도록 개수를 따로 둔다 (원자적 UPDATE로만 바꾼다)
    like_count    INTEGER      NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count INTEGER      NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 하루에 한 번만 인증할 수 있다 (동시에 두 번 올려도 DB가 막는다)
    CONSTRAINT uq_certifications_daily UNIQUE (crew_id, user_id, cert_date)
);

-- 크루 피드: 최신순 커서 페이지네이션 (WHERE crew_id = ? AND id < ? ORDER BY id DESC)
CREATE INDEX idx_certifications_feed ON certifications (crew_id, id DESC);
-- 오늘 인증 현황, 자정 정산 (WHERE crew_id = ? AND cert_date = ?)
CREATE INDEX idx_certifications_crew_date ON certifications (crew_id, cert_date);

CREATE TABLE certification_likes
(
    certification_id BIGINT      NOT NULL REFERENCES certifications (id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (certification_id, user_id)
);

CREATE TABLE certification_comments
(
    id               BIGSERIAL PRIMARY KEY,
    certification_id BIGINT       NOT NULL REFERENCES certifications (id) ON DELETE CASCADE,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    content          VARCHAR(300) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_certification_comments ON certification_comments (certification_id, id);
