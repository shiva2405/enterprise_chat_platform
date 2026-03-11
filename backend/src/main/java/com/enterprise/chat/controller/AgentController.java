package com.enterprise.chat.controller;

import com.enterprise.chat.dto.AgentResponse;
import com.enterprise.chat.dto.ChatSessionDTO;
import com.enterprise.chat.service.AgentService;
import com.enterprise.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class AgentController {
    
    private final ChatService chatService;
    private final AgentService agentService;
    
    @GetMapping("/waiting-chats")
    public ResponseEntity<List<ChatSessionDTO>> getWaitingChats() {
        List<ChatSessionDTO> chats = chatService.getWaitingChats();
        return ResponseEntity.ok(chats);
    }
    
    @GetMapping("/my-chats/{agentId}")
    public ResponseEntity<List<ChatSessionDTO>> getMyChats(@PathVariable Long agentId) {
        List<ChatSessionDTO> chats = chatService.getAgentChats(agentId);
        return ResponseEntity.ok(chats);
    }
    
    @PostMapping("/accept-chat")
    public ResponseEntity<ChatSessionDTO> acceptChat(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Long agentId = Long.valueOf(request.get("agentId").toString());
        ChatSessionDTO session = chatService.assignChatToAgent(sessionId, agentId);
        return ResponseEntity.ok(session);
    }
    
    @PostMapping("/close-chat/{sessionId}")
    public ResponseEntity<ChatSessionDTO> closeChat(@PathVariable String sessionId) {
        ChatSessionDTO session = chatService.closeChat(sessionId);
        return ResponseEntity.ok(session);
    }
    
    @PutMapping("/availability/{agentId}")
    public ResponseEntity<AgentResponse> setAvailability(
            @PathVariable Long agentId,
            @RequestBody Map<String, Boolean> request) {
        boolean available = request.get("available");
        AgentResponse agent = agentService.setAgentAvailability(agentId, available);
        return ResponseEntity.ok(agent);
    }
}
