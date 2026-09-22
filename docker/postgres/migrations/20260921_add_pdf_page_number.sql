-- Apply this once to an existing database created before pdf_page_number was added.
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS pdf_page_number INTEGER;
