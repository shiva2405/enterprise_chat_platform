package com.enterprise.chat.dto;

import com.enterprise.chat.model.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDTO {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String description;
    private boolean indexed;
    private LocalDateTime uploadedAt;

    public static KnowledgeDocumentDTO fromEntity(KnowledgeDocument document) {
        return KnowledgeDocumentDTO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .description(document.getDescription())
                .indexed(document.isIndexed())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
