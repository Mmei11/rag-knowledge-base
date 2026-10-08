package com.example.ragkb.chat.repository;

import com.example.ragkb.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @Modifying
    @Query(value = "DELETE FROM chat_messages WHERE session_id = :sessionId", nativeQuery = true)
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
