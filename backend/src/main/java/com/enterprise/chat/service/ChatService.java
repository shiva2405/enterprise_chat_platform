package com.enterprise.chat.service;

import com.enterprise.chat.dto.ChatMessageDTO;
import com.enterprise.chat.dto.ChatSessionDTO;
import com.enterprise.chat.model.*;
import com.enterprise.chat.repository.ChatMessageRepository;
import com.enterprise.chat.repository.ChatSessionRepository;
import com.enterprise.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    public ChatSessionDTO getSessionBySessionId(String sessionId) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found"));
        return ChatSessionDTO.fromEntity(session);
    }
    
    public List<ChatSessionDTO> getWaitingChats() {
        return chatSessionRepository.findByStatus(ChatStatus.WAITING_FOR_AGENT)
                .stream()
                .map(ChatSessionDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<ChatSessionDTO> getAgentChats(Long agentId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        return chatSessionRepository.findByAgentAndStatus(agent, ChatStatus.ASSIGNED_TO_AGENT)
                .stream()
                .map(ChatSessionDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ChatSessionDTO assignChatToAgent(String sessionId, Long agentId) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found"));
        
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        
        session.setAgent(agent);
        session.setStatus(ChatStatus.ASSIGNED_TO_AGENT);
        session.setAssignedAt(LocalDateTime.now());
        ChatSession savedSession = chatSessionRepository.save(session);
        
        notifyCustomerOfAgentAssignment(savedSession);
        notifyAgentsOfChatAssignment(savedSession);
        
        return ChatSessionDTO.fromEntity(savedSession);
    }
    
    @Transactional
    public ChatMessageDTO sendMessage(String sessionId, SenderType senderType, Long senderId, String content) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found"));
        
        ChatMessage message = ChatMessage.builder()
                .chatSession(session)
                .senderType(senderType)
                .senderId(senderId)
                .content(content)
                .build();
        
        ChatMessage savedMessage = chatMessageRepository.save(message);
        ChatMessageDTO messageDTO = ChatMessageDTO.fromEntity(savedMessage);
        
        broadcastMessage(sessionId, messageDTO);
        
        return messageDTO;
    }
    
    @Transactional
    public ChatSessionDTO closeChat(String sessionId) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found"));
        
        session.setStatus(ChatStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        ChatSession savedSession = chatSessionRepository.save(session);
        
        notifyChatClosed(savedSession);
        
        return ChatSessionDTO.fromEntity(savedSession);
    }
    
    private void notifyAgentsOfNewChat(ChatSession session) {
        ChatSessionDTO dto = ChatSessionDTO.fromEntity(session);
        messagingTemplate.convertAndSend("/topic/agent/new-chat", dto);
        log.info("Notified agents of new chat: {}", session.getSessionId());
    }
    
    private void notifyCustomerOfAgentAssignment(ChatSession session) {
        ChatSessionDTO dto = ChatSessionDTO.fromEntity(session);
        messagingTemplate.convertAndSend("/topic/customer/" + session.getSessionId(), dto);
        log.info("Notified customer {} of agent assignment", session.getSessionId());
    }
    
    private void notifyAgentsOfChatAssignment(ChatSession session) {
        ChatSessionDTO dto = ChatSessionDTO.fromEntity(session);
        messagingTemplate.convertAndSend("/topic/agent/chat-assigned", dto);
    }
    
    private void broadcastMessage(String sessionId, ChatMessageDTO message) {
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, message);
        log.info("Broadcast message to session: {}", sessionId);
    }
    
    private void notifyChatClosed(ChatSession session) {
        ChatSessionDTO dto = ChatSessionDTO.fromEntity(session);
        messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId() + "/closed", dto);
        messagingTemplate.convertAndSend("/topic/agent/chat-closed", dto);
    }
}
