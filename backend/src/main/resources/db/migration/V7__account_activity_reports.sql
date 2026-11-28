-- 회원 탈퇴: 행을 지우지 않고 개인정보를 지운 뒤 탈퇴 시각을 남긴다 (지난 벌금 기록이 사용자를 가리키므로)
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
-- 내 인증에 댓글·좋아요가 달리면 알림을 받을지
ALTER TABLE users ADD COLUMN notify_activity BOOLEAN NOT NULL DEFAULT TRUE;

-- 신고가 쌓이거나 크루장이 가리면 사진을 가린다 (인증 기록·정산에는 그대로 남는다)
ALTER TABLE certifications ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE certification_reports
(
    certification_id BIGINT      NOT NULL REFERENCES certifications (id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES users (id),
    reason           VARCHAR(20) NOT NULL CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'UNRELATED', 'OTHER')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 사람이 같은 인증을 여러 번 신고해 혼자 가릴 수 없게
    PRIMARY KEY (certification_id, user_id)
);
