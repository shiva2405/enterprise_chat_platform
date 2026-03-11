package com.enterprise.chat.service;

import com.enterprise.chat.model.KnowledgeDocument;
import com.enterprise.chat.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final VectorStoreService vectorStoreService;
    private final DocumentProcessingService documentProcessingService;

    @Value("${document.temp.path:./temp/documents}")
    private String tempPath;

    @Transactional
    public KnowledgeDocument uploadDocument(MultipartFile file, String description) throws IOException {
        String fileName = file.getOriginalFilename();
        String fileType = getFileExtension(fileName);

        // Validate file type
        if (!isSupportedFileType(fileType)) {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }

        // Extract content
        String content = extractContent(file, fileType);

        // Create document record without indexing
        KnowledgeDocument document = KnowledgeDocument.builder()
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(file.getSize())
                .content(content)
                .description(description)
                .indexed(false)
                .build();

        KnowledgeDocument savedDocument = knowledgeDocumentRepository.save(document);
        log.info("Saved document: {} with ID: {}", fileName, savedDocument.getId());

        // Save content to temp file for async processing
        String tempFilePath = saveToTempFile(content, savedDocument.getId());

        // Trigger async processing
        documentProcessingService.processDocumentAsync(
                savedDocument.getId(),
                tempFilePath,
                fileName,
                fileType
        );

        return savedDocument;
    }

    private String saveToTempFile(String content, Long documentId) throws IOException {
        Path tempDir = Paths.get(tempPath);
        Files.createDirectories(tempDir);

        String fileName = documentId + "_" + UUID.randomUUID().toString() + ".txt";
        Path tempFile = tempDir.resolve(fileName);

        Files.writeString(tempFile, content);
        log.info("Saved document content to temp file: {}", tempFile.toAbsolutePath());

        return tempFile.toAbsolutePath().toString();
    }

    private boolean isSupportedFileType(String fileType) {
        return fileType != null && (fileType.equalsIgnoreCase("pdf")
                || fileType.equalsIgnoreCase("md")
                || fileType.equalsIgnoreCase("txt"));
    }

    private String extractContent(MultipartFile file, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> extractPdfContent(file);
            case "md", "txt" -> extractTextContent(file);
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }

    private String extractPdfContent(MultipartFile file) throws IOException {
        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdfDocument);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        }
    }

    private String extractTextContent(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("Extracted {} characters from text file", content.length());
        return content;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    public List<KnowledgeDocument> getAllDocuments() {
        return knowledgeDocumentRepository.findAll();
    }

    public KnowledgeDocument getDocument(Long id) {
        return knowledgeDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }

    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = getDocument(id);
        vectorStoreService.removeDocument(id);
        knowledgeDocumentRepository.delete(document);
        log.info("Deleted document: {}", document.getFileName());
    }
}
