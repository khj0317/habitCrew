-- 웹 푸시 구독: 브라우저(기기)마다 하나. 같은 사람이 휴대폰·PC에서 각각 구독할 수 있다
CREATE TABLE push_subscriptions
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 브라우저 회사의 푸시 서버 주소 (구독마다 다름)
    endpoint        VARCHAR(1000) NOT NULL,
    -- 메시지 암호화에 쓰는 브라우저 공개 키와 인증 비밀값 (RFC 8291)
    p256dh          VARCHAR(200) NOT NULL,
    auth            VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

-- 서버가 처음 뜰 때 만들어 두는 값 (VAPID 키 등). 환경 변수로 넣으면 그 값을 쓴다
CREATE TABLE app_secrets
(
    name       VARCHAR(50) PRIMARY KEY,
    value      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 보낸 알림 기록: 같은 날 같은 알림을 두 번 보내지 않게 한다 (서버가 여러 대이거나 다시 돌아도)
CREATE TABLE notification_log
(
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(30) NOT NULL,
    ref_date   DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, kind, ref_date)
);
