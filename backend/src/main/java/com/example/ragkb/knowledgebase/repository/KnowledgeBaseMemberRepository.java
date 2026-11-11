package com.example.ragkb.knowledgebase.repository;

import com.example.ragkb.knowledgebase.entity.KnowledgeBaseMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseMemberRepository extends JpaRepository<KnowledgeBaseMember, Long> {

    Optional<KnowledgeBaseMember> findByKnowledgeBaseIdAndUserId(Long knowledgeBaseId, Long userId);

    List<KnowledgeBaseMember> findByKnowledgeBaseIdOrderByCreatedAtAsc(Long knowledgeBaseId);

    List<KnowledgeBaseMember> findByUserId(Long userId);

    boolean existsByKnowledgeBaseIdAndUserId(Long knowledgeBaseId, Long userId);

    long deleteByKnowledgeBaseIdAndUserId(Long knowledgeBaseId, Long userId);

    long countByKnowledgeBaseId(Long knowledgeBaseId);

    @Query("select m.userId from KnowledgeBaseMember m where m.knowledgeBaseId = :knowledgeBaseId")
    List<Long> findUserIdsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
