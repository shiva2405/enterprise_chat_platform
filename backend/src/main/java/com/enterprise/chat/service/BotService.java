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

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[\\d\\s\\-+()]{8,}$");

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
        replies.add(saveBotMessage(session, "Welcome to Enterprise Support! I'm here to help connect you with one of our agents."));
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
                    session.setBotStep("ASK_PROBLEM");
                    session.setCustomerPhone(trimmedInput);
                    botReplies.add(saveBotMessage(session, "Perfect! Now, please describe your issue or question in detail."));
                }
            }
            case "ASK_PROBLEM" -> {
                if (trimmedInput.length() < 10) {
                    botReplies.add(saveBotMessage(session, "Please provide more details about your issue (at least 10 characters)."));
                } else {
                    session.setBotStep("COMPLETED");
                    botReplies.add(saveBotMessage(session, "Thank you for providing your information. Connecting you with an available agent..."));

                    Customer customer = Customer.builder()
                            .name(session.getCustomerName())
                            .email(session.getCustomerEmail())
                            .phone(session.getCustomerPhone())
                            .problem(trimmedInput)
                            .sessionId(sessionId)
                            .build();
                    Customer savedCustomer = customerRepository.save(customer);

                    session.setCustomer(savedCustomer);
                    session.setStatus(ChatStatus.WAITING_FOR_AGENT);

                    botReplies.add(saveBotMessage(session, "Please wait while we connect you with an agent. This usually takes less than a minute."));

                    chatSessionRepository.save(session);
                    notifyAgentsOfNewChat(session);

                    return new BotReplyResult(customerMsg, botReplies, session.getStatus().name(), session.getBotStep());
                }
            }
            default -> {
                botReplies.add(saveBotMessage(session, "I'm sorry, I didn't understand. Please wait for an agent."));
            }
        }

        chatSessionRepository.save(session);
        return new BotReplyResult(customerMsg, botReplies, session.getStatus().name(), session.getBotStep());
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
