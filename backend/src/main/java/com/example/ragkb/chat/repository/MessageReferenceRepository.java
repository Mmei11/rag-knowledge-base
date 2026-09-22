package com.example.ragkb.chat.repository;

import com.example.ragkb.chat.entity.MessageReference;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageReferenceRepository extends JpaRepository<MessageReference, Long> {

    List<MessageReference> findByMessageIdOrderByIdAsc(Long messageId);

    @Query(value = """
            SELECT mr.message_id AS "messageId",
                   mr.document_id AS "documentId",
                   mr.chunk_id AS "chunkId",
                   c.chunk_index AS "chunkIndex",
                   c.content AS content,
                   mr.similarity AS similarity,
                   d.file_name AS "fileName",
                   c.page_number AS "pageNumber",
                   c.pdf_page_number AS "pdfPageNumber"
            FROM message_references mr
            JOIN document_chunks c ON c.id = mr.chunk_id
            JOIN documents d ON d.id = mr.document_id
            WHERE mr.message_id IN (:messageIds)
            ORDER BY mr.message_id, mr.id
            """, nativeQuery = true)
    List<MessageReferenceDetail> findDetailsByMessageIdIn(@Param("messageIds") List<Long> messageIds);
}
