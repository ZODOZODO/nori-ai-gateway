-- 기존 Job이 없는 개발 단계에만 적용하며 가짜 권한으로 기존 행을 보정하지 않습니다.
ALTER TABLE gw.job
    ADD COLUMN request_perm text[] NOT NULL,
    ADD COLUMN request_pdg text NOT NULL;
