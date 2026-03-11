package com.enterprise.chat.repository;

import com.enterprise.chat.model.ChatMessage;
import com.enterprise.chat.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatSessionOrderByTimestampAsc(ChatSession chatSession);
}
