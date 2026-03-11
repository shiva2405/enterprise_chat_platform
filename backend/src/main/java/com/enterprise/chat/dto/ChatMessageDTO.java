package com.enterprise.chat.dto;

import com.enterprise.chat.model.ChatMessage;
import com.enterprise.chat.model.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private Long id;
    private String sessionId;
    private SenderType senderType;
    private Long senderId;
    private String content;
    private LocalDateTime timestamp;
    
    public static ChatMessageDTO fromEntity(ChatMessage message) {
        return ChatMessageDTO.builder()
                .id(message.getId())
                .sessionId(message.getChatSession().getSessionId())
                .senderType(message.getSenderType())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}
