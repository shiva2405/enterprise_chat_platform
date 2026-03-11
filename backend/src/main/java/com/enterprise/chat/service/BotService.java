package com.enterprise.chat.service;

import com.enterprise.chat.dto.ChatMessageDTO;
import com.enterprise.chat.dto.CustomerInfoRequest;
import com.enterprise.chat.model.*;
import com.enterprise.chat.repository.ChatMessageRepository;
import com.enterprise.chat.repository.ChatSessionRepository;
import com.enterprise.chat.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CustomerRepository customerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OllamaService ollamaService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[\\d\\s\\-+()]{8,}$");
    private static final String AGENT_KEYWORD = "AGENT";

    @Transactional
    public List<ChatMessageDTO> startConversation(String sessionId) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    ChatSession newSession = ChatSession.builder()
                            .sessionId(sessionId)
                            .status(ChatStatus.BOT_INTERACTION)
                            .botStep("GREETING")
                            .build();
                    return chatSessionRepository.save(newSession);
                });

        if (!"GREETING".equals(session.getBotStep())) {
            return session.getMessages().stream()
                    .map(ChatMessageDTO::fromEntity)
                    .collect(Collectors.toList());
        }

        List<ChatMessageDTO> replies = new ArrayList<>();
        replies.add(saveBotMessage(session, "Welcome to Enterprise Support! I'm an AI assistant here to help you."));
        replies.add(saveBotMessage(session, "May I know your name please?"));

        session.setBotStep("ASK_NAME");
        chatSessionRepository.save(session);

        return replies;
    }

    @Transactional
    public BotReplyResult handleCustomerMessage(String sessionId, String userInput) {
        ChatSession session = chatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        ChatMessageDTO customerMsg = saveCustomerMessage(session, userInput);

        List<ChatMessageDTO> botReplies = new ArrayList<>();
        String currentStep = session.getBotStep();
        String trimmedInput = userInput.trim();

        // Check if customer wants to talk to an agent
        if (trimmedInput.equalsIgnoreCase(AGENT_KEYWORD)) {
            return handleAgentRequest(session, customerMsg, botReplies);
        }

        // Handle Ollama mode - continuous conversation with bot
        if (session.isInOllamaMode()) {
            return handleOllamaConversation(session, customerMsg, trimmedInput, botReplies);
        }

        switch (currentStep) {
            case "ASK_NAME" -> {
                if (trimmedInput.length() < 2) {
                    botReplies.add(saveBotMessage(session, "Please enter a valid name (at least 2 characters)."));
                } else {
                    session.setBotStep("ASK_EMAIL");
                    session.setCustomerName(trimmedInput);
                    botReplies.add(saveBotMessage(session, "Nice to meet you, " + trimmedInput + "! What's your email address?"));
                }
            }
            case "ASK_EMAIL" -> {
                if (!EMAIL_PATTERN.matcher(trimmedInput).matches()) {
                    botReplies.add(saveBotMessage(session, "Please enter a valid email address (e.g., name@example.com)."));
                } else {
                    session.setBotStep("ASK_PHONE");
                    session.setCustomerEmail(trimmedInput);
                    botReplies.add(saveBotMessage(session, "Great! And your phone number?"));
                }
            }
            case "ASK_PHONE" -> {
                if (!PHONE_PATTERN.matcher(trimmedInput).matches()) {
                    botReplies.add(saveBotMessage(session, "Please enter a valid phone number."));
                } else {
                    session.setBotStep("OLLAMA_CHAT");
                    session.setCustomerPhone(trimmedInput);
                    session.setInOllamaMode(true);
                    botReplies.add(saveBotMessage(session, "Perfect! I'm now ready to help you. Please describe your issue or ask any question. Type 'AGENT' at any time if you want to speak with a human agent."));
                }
            }
            case "OLLAMA_CHAT" -> {
                return handleOllamaConversation(session, customerMsg, trimmedInput, botReplies);
            }
            default -> {
                botReplies.add(saveBotMessage(session, "I'm sorry, I didn't understand. Please wait for an agent."));
            }
        }

        chatSessionRepository.save(session);
        return new BotReplyResult(customerMsg, botReplies, session.getStatus().name(), session.getBotStep());
    }

    private BotReplyResult handleOllamaConversation(ChatSession session, ChatMessageDTO customerMsg, 
                                                     String userInput, List<ChatMessageDTO> botReplies) {
        // Build customer context
        String customerContext = String.format("Name: %s, Email: %s, Phone: %s",
                session.getCustomerName(), session.getCustomerEmail(), session.getCustomerPhone());

        // Get conversation history for context
        List<String> conversationHistory = getConversationHistory(session);

        // Generate Ollama response with RAG
        String ollamaResponse = ollamaService.generateResponse(userInput, customerContext, conversationHistory);
        botReplies.add(saveBotMessage(session, ollamaResponse));

        // Store the problem description if this is the first message
        if (session.getCustomerProblem() == null || session.getCustomerProblem().isEmpty()) {
            session.setCustomerProblem(userInput);
        }

        chatSessionRepository.save(session);
        return new BotReplyResult(customerMsg, botReplies, session.getStatus().name(), session.getBotStep());
    }

    private BotReplyResult handleAgentRequest(ChatSession session, ChatMessageDTO customerMsg, 
                                               List<ChatMessageDTO> botReplies) {
        log.info("Customer {} requested to talk to an agent", session.getSessionId());

        botReplies.add(saveBotMessage(session, "I'll connect you with a human agent now. Please wait..."));

        // Create customer record if not exists
        if (session.getCustomer() == null && session.getCustomerName() != null) {
            Customer customer = Customer.builder()
                    .name(session.getCustomerName())
                    .email(session.getCustomerEmail() != null ? session.getCustomerEmail() : "")
                    .phone(session.getCustomerPhone() != null ? session.getCustomerPhone() : "")
                    .problem(session.getCustomerProblem() != null ? session.getCustomerProblem() : "Customer requested agent")
                    .sessionId(session.getSessionId())
                    .build();
            Customer savedCustomer = customerRepository.save(customer);
            session.setCustomer(savedCustomer);
        }

        session.setBotStep("COMPLETED");
        session.setInOllamaMode(false);
        session.setStatus(ChatStatus.WAITING_FOR_AGENT);

        botReplies.add(saveBotMessage(session, "You are now in the queue. An agent will be with you shortly."));

        chatSessionRepository.save(session);
        notifyAgentsOfNewChat(session);

        return new BotReplyResult(customerMsg, botReplies, session.getStatus().name(), session.getBotStep());
    }

    private List<String> getConversationHistory(ChatSession session) {
        List<ChatMessage> messages = session.getMessages();
        // Get last 10 messages for context
        int startIndex = Math.max(0, messages.size() - 10);
        return messages.subList(startIndex, messages.size()).stream()
                .map(msg -> String.format("%s: %s", 
                        msg.getSenderType() == SenderType.CUSTOMER ? "Customer" : "Bot",
                        msg.getContent()))
                .collect(Collectors.toList());
    }

    private ChatMessageDTO saveBotMessage(ChatSession session, String content) {
        ChatMessage message = ChatMessage.builder()
                .chatSession(session)
                .senderType(SenderType.BOT)
                .content(content)
                .build();
        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageDTO dto = ChatMessageDTO.fromEntity(saved);

        messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), dto);
        return dto;
    }

    private ChatMessageDTO saveCustomerMessage(ChatSession session, String content) {
        ChatMessage message = ChatMessage.builder()
                .chatSession(session)
                .senderType(SenderType.CUSTOMER)
                .content(content)
                .build();
        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageDTO dto = ChatMessageDTO.fromEntity(saved);

        messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(), dto);
        return dto;
    }

    private void notifyAgentsOfNewChat(ChatSession session) {
        var dto = com.enterprise.chat.dto.ChatSessionDTO.fromEntity(session);
        messagingTemplate.convertAndSend("/topic/agent/new-chat", dto);
        log.info("Notified agents of new chat: {}", session.getSessionId());
    }

    public record BotReplyResult(
            ChatMessageDTO customerMessage,
            List<ChatMessageDTO> botReplies,
            String status,
            String botStep
    ) {}
}
