package com.example.ragkb.knowledgebase.repository;

import com.example.ragkb.knowledgebase.entity.KnowledgeBase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

    List<KnowledgeBase> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select kb from KnowledgeBase kb
            where kb.userId = :userId
               or exists (
                   select 1 from KnowledgeBaseMember m
                   where m.knowledgeBaseId = kb.id and m.userId = :userId
               )
            order by kb.updatedAt desc
            """)
    List<KnowledgeBase> findAccessibleByUserId(@Param("userId") Long userId);
}
