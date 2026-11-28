-- 앱 안 알림함: 댓글·좋아요·벌금·새 크루원. 푸시 알림을 허용하지 않은 사람도 앱에서 볼 수 있다
CREATE TABLE notifications
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('COMMENT', 'LIKE', 'PENALTY', 'MEMBER_JOINED')),
    title      VARCHAR(100) NOT NULL,
    body       VARCHAR(200) NOT NULL,
    -- 눌렀을 때 열 화면 (같은 사이트 안의 경로)
    url        VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

-- 내 알림 최신순 + 안 읽은 개수
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
