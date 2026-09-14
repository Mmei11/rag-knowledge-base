package com.example.ragkb.chat.repository;

import com.example.ragkb.chat.entity.MessageReference;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReferenceRepository extends JpaRepository<MessageReference, Long> {

    List<MessageReference> findByMessageIdOrderByIdAsc(Long messageId);
}
