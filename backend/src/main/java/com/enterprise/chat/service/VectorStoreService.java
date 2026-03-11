package com.enterprise.chat.service;

import com.enterprise.chat.model.KnowledgeDocument;
import com.enterprise.chat.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStoreService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${vector.store.path:./vector-store.json}")
    private String vectorStorePath;

    private VectorStore vectorStore;

    @PostConstruct
    public void init() {
        vectorStore = new SimpleVectorStore(embeddingModel);
        loadExistingVectorStore();
        indexPendingDocuments();
    }

    private void loadExistingVectorStore() {
        File file = new File(vectorStorePath);
        if (file.exists()) {
            try {
                ((SimpleVectorStore) vectorStore).load(file);
                log.info("Loaded existing vector store from: {}", vectorStorePath);
            } catch (Exception e) {
                log.warn("Could not load existing vector store: {}", e.getMessage());
            }
        }
    }

    private void saveVectorStore() {
        try {
            Path path = Paths.get(vectorStorePath);
            Files.createDirectories(path.getParent());
            ((SimpleVectorStore) vectorStore).save(new File(vectorStorePath));
            log.info("Saved vector store to: {}", vectorStorePath);
        } catch (IOException e) {
            log.error("Could not save vector store: {}", e.getMessage());
        }
    }

    @Transactional
    public void indexPendingDocuments() {
        List<KnowledgeDocument> pendingDocs = knowledgeDocumentRepository.findByIndexedFalse();
        if (pendingDocs.isEmpty()) {
            log.info("No pending documents to index");
            return;
        }

        log.info("Indexing {} pending documents", pendingDocs.size());

        List<Document> documents = new ArrayList<>();
        for (KnowledgeDocument doc : pendingDocs) {
            List<String> chunks = chunkText(doc.getContent(), 600);
            for (int i = 0; i < chunks.size(); i++) {
                Document document = new Document(
                        chunks.get(i),
                        Map.of(
                                "documentId", doc.getId().toString(),
                                "fileName", doc.getFileName(),
                                "fileType", doc.getFileType(),
                                "chunkIndex", String.valueOf(i)
                        )
                );
                documents.add(document);
            }
            doc.setIndexed(true);
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            knowledgeDocumentRepository.saveAll(pendingDocs);
            saveVectorStore();
            log.info("Indexed {} document chunks from {} documents", documents.size(), pendingDocs.size());
        }
    }

    public void indexDocument(KnowledgeDocument document) {
        log.info("Indexing document: {}", document.getFileName());

        List<String> chunks = chunkText(document.getContent(), 600);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Document doc = new Document(
                    chunks.get(i),
                    Map.of(
                            "documentId", document.getId().toString(),
                            "fileName", document.getFileName(),
                            "fileType", document.getFileType(),
                            "chunkIndex", String.valueOf(i)
                    )
            );
            documents.add(doc);
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            saveVectorStore();
            log.info("Indexed {} chunks for document: {}", documents.size(), document.getFileName());
        }
    }

    public List<String> searchRelevantContext(String query, int topK) {
        try {
            log.info("Searching for relevant context for query: {}", query);

            // Use only original query for better embedding accuracy
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(topK)
            );

            log.info("Query returned {} results", results.size());

            // Deduplicate contexts while preserving order (maintains semantic ranking)
            Set<String> seenContents = new LinkedHashSet<>();
            List<String> deduplicatedContexts = new ArrayList<>();

            for (Document doc : results) {
                String fileName = (String) doc.getMetadata().get("fileName");
                String content = doc.getContent();
                String formattedContext = String.format("[Source: %s]\n%s", fileName, content);

                // Use content as key for deduplication (normalize whitespace)
                String normalizedContent = content.replaceAll("\\s+", " ").trim();

                if (!seenContents.contains(normalizedContent)) {
                    seenContents.add(normalizedContent);
                    deduplicatedContexts.add(formattedContext);
                }
            }

            log.info("Retrieved {} contexts, {} after deduplication", results.size(), deduplicatedContexts.size());

            return deduplicatedContexts;
        } catch (Exception e) {
            log.error("Error searching vector store: {}", e.getMessage());
            return List.of();
        }
    }

    public SearchResult searchWithRelevanceCheck(String query, int topK, double similarityThreshold) {
        try {
            log.info("Searching with relevance check for query: {} (threshold: {})", query, similarityThreshold);

            // Use only original query for better embedding accuracy
            // Increased topK to get more candidates for better selection
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.query(query).withTopK(topK * 2)
            );

            if (results.isEmpty()) {
                log.info("No documents found in vector store");
                return new SearchResult(false, List.of(), 0.0);
            }

            // Deduplicate contexts while preserving semantic ranking order
            Set<String> seenContents = new LinkedHashSet<>();
            List<String> deduplicatedContexts = new ArrayList<>();

            for (Document doc : results) {
                String fileName = (String) doc.getMetadata().get("fileName");
                String content = doc.getContent();
                String formattedContext = String.format("[Source: %s]\n%s", fileName, content);

                // Use content as key for deduplication (normalize whitespace)
                String normalizedContent = content.replaceAll("\\s+", " ").trim();

                if (!seenContents.contains(normalizedContent)) {
                    seenContents.add(normalizedContent);
                    deduplicatedContexts.add(formattedContext);
                }
            }

            // Take top 5 most relevant (preserving order from similarity search)
            List<String> topContexts = deduplicatedContexts.stream()
                    .limit(5)
                    .collect(Collectors.toList());

            // Calculate relevance based on number of unique results found
            double relevanceScore = Math.min(deduplicatedContexts.size() / (double) topK, 1.0);
            boolean isRelevant = !topContexts.isEmpty() && relevanceScore >= similarityThreshold;

            log.info("Retrieved {} raw, {} unique, returning top {} with relevance: {}, isRelevant: {}",
                    results.size(), deduplicatedContexts.size(), topContexts.size(), relevanceScore, isRelevant);

            return new SearchResult(isRelevant, topContexts, relevanceScore);

        } catch (Exception e) {
            log.error("Error searching vector store: {}", e.getMessage());
            return new SearchResult(false, List.of(), 0.0);
        }
    }

    public record SearchResult(
            boolean isRelevant,
            List<String> contexts,
            double relevanceScore
    ) {}

    private List<String> chunkText(String text, int targetChunkSize) {
        List<String> chunks = new ArrayList<>();

        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // First, split by major structural elements (headers)
        List<Section> sections = extractSectionsWithHeaders(text);
        log.info("Document split into {} sections", sections.size());

        for (Section section : sections) {
            String header = section.header();
            String content = section.content();

            if (content.isEmpty()) {
                // Just a header with no content yet
                continue;
            }

            // Combine header and content
            String fullSection = header.isEmpty() ? content : header + "\n" + content;

            if (fullSection.length() <= targetChunkSize) {
                // Section is small enough, keep it as is
                chunks.add(fullSection);
            } else {
                // Section is too large, split it intelligently preserving structure
                List<String> sectionChunks = splitSectionPreservingStructure(header, content, targetChunkSize);
                chunks.addAll(sectionChunks);
            }
        }

        log.info("Content-aware chunking produced {} chunks from document", chunks.size());

        // Log chunk sizes for debugging
        for (int i = 0; i < chunks.size(); i++) {
            log.debug("Chunk {} size: {} characters", i, chunks.get(i).length());
        }

        return chunks;
    }

    private record Section(String header, String content) {}

    private List<Section> extractSectionsWithHeaders(String text) {
        List<Section> sections = new ArrayList<>();
        String[] lines = text.split("\n");

        String currentHeader = "";
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Check if this is a markdown header
            if (trimmedLine.matches("^#{1,6}\\s+.+")) {
                // Save previous section if exists
                if (currentContent.length() > 0 || !currentHeader.isEmpty()) {
                    sections.add(new Section(currentHeader, currentContent.toString().trim()));
                }
                // Start new section with this header
                currentHeader = trimmedLine;
                currentContent = new StringBuilder();
            } else {
                // Add to current content
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
            }
        }

        // Don't forget the last section
        if (currentContent.length() > 0 || !currentHeader.isEmpty()) {
            sections.add(new Section(currentHeader, currentContent.toString().trim()));
        }

        // If no headers found, treat entire text as one section
        if (sections.isEmpty() && !text.trim().isEmpty()) {
            sections.add(new Section("", text.trim()));
        }

        return sections;
    }

    private List<String> splitSectionPreservingStructure(String header, String content, int targetChunkSize) {
        List<String> chunks = new ArrayList<>();

        // First, try to split by structural elements (tables, lists, code blocks)
        List<String> structuralElements = extractStructuralElements(content);

        StringBuilder currentChunk = new StringBuilder();
        if (!header.isEmpty()) {
            currentChunk.append(header).append("\n");
        }
        int headerLength = currentChunk.length();

        for (String element : structuralElements) {
            // If adding this element would exceed target size and we have content
            if (currentChunk.length() + element.length() > targetChunkSize &&
                currentChunk.length() > headerLength) {

                // Save current chunk
                chunks.add(currentChunk.toString().trim());

                // Start new chunk with header
                currentChunk = new StringBuilder();
                if (!header.isEmpty()) {
                    currentChunk.append(header).append("\n");
                }
            }

            if (currentChunk.length() > headerLength) {
                currentChunk.append("\n");
            }
            currentChunk.append(element);
        }

        // Don't forget the last chunk
        if (currentChunk.length() > headerLength) {
            chunks.add(currentChunk.toString().trim());
        }

        // If still no chunks (very large single element), fall back to sentence splitting
        if (chunks.isEmpty() && !content.isEmpty()) {
            chunks.addAll(splitBySentencesWithHeader(header, content, targetChunkSize));
        }

        return chunks;
    }

    private List<String> extractStructuralElements(String content) {
        List<String> elements = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentElement = new StringBuilder();
        String currentType = "paragraph"; // paragraph, table, list, code

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Detect element type
            String lineType = detectLineType(trimmedLine, currentType);

            // If type changed and we have content, save current element
            if (!lineType.equals(currentType) && currentElement.length() > 0) {
                elements.add(currentElement.toString().trim());
                currentElement = new StringBuilder();
            }

            currentType = lineType;

            if (currentElement.length() > 0) {
                currentElement.append("\n");
            }
            currentElement.append(line);
        }

        // Don't forget the last element
        if (currentElement.length() > 0) {
            elements.add(currentElement.toString().trim());
        }

        return elements;
    }

    private String detectLineType(String line, String currentType) {
        // Table detection (markdown table with | or separator line)
        if (line.contains("|") || line.matches("^[-|:\s]+$")) {
            return "table";
        }

        // List detection
        if (line.matches("^[-*+•]\\s+.+|^\\d+[.)]\\s+.+")) {
            return "list";
        }

        // Code block detection
        if (line.startsWith("```") || line.startsWith("    ") || line.startsWith("\t")) {
            return "code";
        }

        // Empty line - continue current type
        if (line.isEmpty()) {
            return currentType;
        }

        // Default to paragraph
        return "paragraph";
    }

    private List<String> splitBySentencesWithHeader(String header, String content, int targetChunkSize) {
        List<String> chunks = new ArrayList<>();
        List<String> sentences = splitIntoSentences(content);

        StringBuilder currentChunk = new StringBuilder();
        if (!header.isEmpty()) {
            currentChunk.append(header).append("\n");
        }
        int headerLength = currentChunk.length();

        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > targetChunkSize &&
                currentChunk.length() > headerLength) {

                chunks.add(currentChunk.toString().trim());

                currentChunk = new StringBuilder();
                if (!header.isEmpty()) {
                    currentChunk.append(header).append("\n");
                }
            }

            currentChunk.append(sentence).append(" ");
        }

        if (currentChunk.length() > headerLength) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // Normalize whitespace but preserve newlines for structure
        text = text.replaceAll("[ \t]+", " ").trim();

        // Split by sentence boundaries
        String[] rawSplits = text.split("(?<=[.!?])\\s+(?=[A-Z])|(?<=[.!?])\\s*$");

        for (String raw : rawSplits) {
            String trimmed = raw.trim();
            if (trimmed.length() > 10) {
                sentences.add(trimmed);
            }
        }

        // Fallback if no sentences found
        if (sentences.isEmpty() && text.length() > 10) {
            sentences.add(text);
        }

        return sentences;
    }

    public void removeDocument(Long documentId) {
        // Note: SimpleVectorStore doesn't support deletion by metadata
        // For production, consider using a proper vector database like Pinecone, Weaviate, etc.
        log.warn("Document removal not fully supported with SimpleVectorStore. Document ID: {}", documentId);
    }
}
