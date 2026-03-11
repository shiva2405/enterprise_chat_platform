package com.enterprise.chat.repository;

import com.enterprise.chat.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findByIndexedFalse();
    List<KnowledgeDocument> findByFileType(String fileType);
}
