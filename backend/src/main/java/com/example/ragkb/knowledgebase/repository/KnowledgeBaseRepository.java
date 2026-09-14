package com.example.ragkb.knowledgebase.repository;

import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    List<KnowledgeBase> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);
}
