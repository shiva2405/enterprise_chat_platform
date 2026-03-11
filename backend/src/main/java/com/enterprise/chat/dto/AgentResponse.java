package com.enterprise.chat.dto;

import com.enterprise.chat.model.User;
import com.enterprise.chat.model.UserRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AgentResponse {
    private Long id;
    private String username;
    private String fullName;
    private UserRole role;
    private boolean active;
    private boolean available;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    
    public static AgentResponse fromUser(User user) {
        return AgentResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.isActive())
                .available(user.isAvailable())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
