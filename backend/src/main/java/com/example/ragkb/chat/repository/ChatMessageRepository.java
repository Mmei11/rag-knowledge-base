package com.example.ragkb.chat.repository;

import com.example.ragkb.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
