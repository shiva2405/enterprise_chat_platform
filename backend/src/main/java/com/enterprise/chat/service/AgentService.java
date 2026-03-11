package com.enterprise.chat.service;

import com.enterprise.chat.dto.AgentResponse;
import com.enterprise.chat.dto.CreateAgentRequest;
import com.enterprise.chat.model.User;
import com.enterprise.chat.model.UserRole;
import com.enterprise.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AgentResponse createAgent(CreateAgentRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        User agent = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(UserRole.AGENT)
                .active(true)
                .available(false)
                .build();
        
        User savedAgent = userRepository.save(agent);
        return AgentResponse.fromUser(savedAgent);
    }
    
    public List<AgentResponse> getAllAgents() {
        return userRepository.findByRole(UserRole.AGENT)
                .stream()
                .map(AgentResponse::fromUser)
                .collect(Collectors.toList());
    }
    
    public AgentResponse getAgentById(Long id) {
        User agent = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        return AgentResponse.fromUser(agent);
    }
    
    public AgentResponse toggleAgentStatus(Long id) {
        User agent = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        agent.setActive(!agent.isActive());
        userRepository.save(agent);
        return AgentResponse.fromUser(agent);
    }
    
    public AgentResponse setAgentAvailability(Long id, boolean available) {
        User agent = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        agent.setAvailable(available);
        userRepository.save(agent);
        return AgentResponse.fromUser(agent);
    }
    
    public void deleteAgent(Long id) {
        User agent = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        if (agent.getRole() == UserRole.ADMIN) {
            throw new RuntimeException("Cannot delete admin user");
        }
        userRepository.delete(agent);
    }
    
    public List<AgentResponse> getAvailableAgents() {
        return userRepository.findByRoleAndAvailable(UserRole.AGENT, true)
                .stream()
                .map(AgentResponse::fromUser)
                .collect(Collectors.toList());
    }
}
