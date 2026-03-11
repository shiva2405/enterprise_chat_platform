package com.enterprise.chat.controller;

import com.enterprise.chat.dto.ChatMessageDTO;
import com.enterprise.chat.dto.ChatSessionDTO;
import com.enterprise.chat.model.SenderType;
import com.enterprise.chat.service.BotService;
import com.enterprise.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final BotService botService;

    @PostMapping("/start")
    public ResponseEntity<List<ChatMessageDTO>> startChat(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        List<ChatMessageDTO> messages = botService.startConversation(sessionId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/bot-reply")
    public ResponseEntity<BotService.BotReplyResult> botReply(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String message = request.get("message");
        BotService.BotReplyResult result = botService.handleCustomerMessage(sessionId, message);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ChatSessionDTO> getSession(@PathVariable String sessionId) {
        ChatSessionDTO session = chatService.getSessionBySessionId(sessionId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/message")
    public ResponseEntity<ChatMessageDTO> sendMessage(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        SenderType senderType = SenderType.valueOf((String) request.get("senderType"));
        Long senderId = request.get("senderId") != null ? Long.valueOf(request.get("senderId").toString()) : null;
        String content = (String) request.get("content");

        ChatMessageDTO message = chatService.sendMessage(sessionId, senderType, senderId, content);
        return ResponseEntity.ok(message);
    }
}
