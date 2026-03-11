package com.enterprise.chat.repository;

import com.enterprise.chat.model.ChatSession;
import com.enterprise.chat.model.ChatStatus;
import com.enterprise.chat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findBySessionId(String sessionId);
    List<ChatSession> findByStatus(ChatStatus status);
    List<ChatSession> findByAgent(User agent);
    List<ChatSession> findByAgentAndStatus(User agent, ChatStatus status);
}
