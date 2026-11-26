-- 체험 계정: 값이 있으면 체험 계정이고, 이 시각이 지나면 정리 작업이 지운다 (일반 회원은 NULL)
ALTER TABLE users ADD COLUMN guest_expires_at TIMESTAMPTZ;

CREATE INDEX idx_users_guest_expires ON users (guest_expires_at) WHERE guest_expires_at IS NOT NULL;
