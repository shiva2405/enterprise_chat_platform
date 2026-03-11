package com.enterprise.chat.websocket;

import com.enterprise.chat.dto.ChatMessageDTO;
import com.enterprise.chat.model.SenderType;
import com.enterprise.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {
    
    private final ChatService chatService;
    
    @MessageMapping("/chat/{sessionId}")
    public void handleChatMessage(@DestinationVariable String sessionId, @Payload Map<String, Object> payload) {
        log.info("Received message for session {}: {}", sessionId, payload);
        
        SenderType senderType = SenderType.valueOf((String) payload.get("senderType"));
        Long senderId = payload.get("senderId") != null ? 
                Long.valueOf(payload.get("senderId").toString()) : null;
        String content = (String) payload.get("content");
        
        ChatMessageDTO message = chatService.sendMessage(sessionId, senderType, senderId, content);
        log.info("Message saved and broadcast: {}", message);
    }
}
