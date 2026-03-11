package com.enterprise.chat.service;

import com.enterprise.chat.model.KnowledgeDocument;
import com.enterprise.chat.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final VectorStoreService vectorStoreService;

    @Value("${document.temp.path:./temp/documents}")
    private String tempPath;

    @Async("taskExecutor")
    @Transactional
    public void processDocumentAsync(Long documentId, String tempFilePath, String fileName, String fileType) {
        log.info("Starting async processing for document: {} (ID: {})", fileName, documentId);

        try {
            // Read content from temp file
            Path path = Paths.get(tempFilePath);
            String content = Files.readString(path);

            // Update document with content
            KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

            document.setContent(content);
            knowledgeDocumentRepository.save(document);

            log.info("Document content loaded for: {} ({} characters)", fileName, content.length());

            // Index the document for RAG
            vectorStoreService.indexDocument(document);

            document.setIndexed(true);
            knowledgeDocumentRepository.save(document);

            log.info("Completed async processing for document: {} (ID: {})", fileName, documentId);

            // Clean up temp file
            Files.deleteIfExists(path);
            log.info("Cleaned up temp file: {}", tempFilePath);

        } catch (Exception e) {
            log.error("Error processing document {}: {}", documentId, e.getMessage(), e);

            // Update document status to failed
            try {
                KnowledgeDocument document = knowledgeDocumentRepository.findById(documentId).orElse(null);
                if (document != null) {
                    document.setIndexed(false);
                    knowledgeDocumentRepository.save(document);
                }
            } catch (Exception ex) {
                log.error("Error updating document status: {}", ex.getMessage());
            }
        }
    }
}
