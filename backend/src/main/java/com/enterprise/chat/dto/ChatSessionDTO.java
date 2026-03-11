package com.enterprise.chat.dto;

import com.enterprise.chat.model.ChatSession;
import com.enterprise.chat.model.ChatStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class ChatSessionDTO {
    private Long id;
    private String sessionId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String problem;
    private ChatStatus status;
    private String botStep;
    private Long agentId;
    private String agentName;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private List<ChatMessageDTO> messages;
    
    public static ChatSessionDTO fromEntity(ChatSession session) {
        return ChatSessionDTO.builder()
                .id(session.getId())
                .sessionId(session.getSessionId())
                .customerName(session.getCustomer() != null ? session.getCustomer().getName() : null)
                .customerEmail(session.getCustomer() != null ? session.getCustomer().getEmail() : null)
                .customerPhone(session.getCustomer() != null ? session.getCustomer().getPhone() : null)
                .problem(session.getCustomer() != null ? session.getCustomer().getProblem() : null)
                .status(session.getStatus())
                .botStep(session.getBotStep())
                .agentId(session.getAgent() != null ? session.getAgent().getId() : null)
                .agentName(session.getAgent() != null ? session.getAgent().getFullName() : null)
                .createdAt(session.getCreatedAt())
                .assignedAt(session.getAssignedAt())
                .messages(session.getMessages() != null ? 
                        session.getMessages().stream()
                                .map(ChatMessageDTO::fromEntity)
                                .collect(Collectors.toList()) : 
                        List.of())
                .build();
    }
}
