ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS search_text TEXT;

CREATE INDEX IF NOT EXISTS idx_document_chunks_search_text_fts
    ON document_chunks USING GIN (to_tsvector('simple', search_text));

DROP INDEX IF EXISTS idx_document_chunks_content_fts;
