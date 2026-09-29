-- Apply this once to an existing database before deploying parse progress support.
ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS parse_progress INTEGER NOT NULL DEFAULT 0;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS parse_stage VARCHAR(100) NOT NULL DEFAULT '等待解析';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_documents_parse_progress'
    ) THEN
        ALTER TABLE documents
            ADD CONSTRAINT ck_documents_parse_progress
            CHECK (parse_progress BETWEEN 0 AND 100);
    END IF;
END $$;

UPDATE documents
SET parse_progress = 100,
    parse_stage = '解析完成'
WHERE status = 'COMPLETED'
  AND parse_progress = 0;
