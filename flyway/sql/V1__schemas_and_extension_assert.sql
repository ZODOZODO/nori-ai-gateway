-- 확장 설치 책임은 superuser bootstrap에만 두고 여기서는 계약을 검증합니다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector' AND extversion = '0.8.7') THEN
        RAISE EXCEPTION '필수 vector 확장 버전이 일치하지 않습니다';
    END IF;
END $$;
CREATE SCHEMA gw;
CREATE SCHEMA kn;
