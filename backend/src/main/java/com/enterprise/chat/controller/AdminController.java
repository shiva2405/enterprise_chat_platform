package com.enterprise.chat.controller;

import com.enterprise.chat.dto.AgentResponse;
import com.enterprise.chat.dto.CreateAgentRequest;
import com.enterprise.chat.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final AgentService agentService;
    
    @PostMapping("/agents")
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        AgentResponse response = agentService.createAgent(request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/agents")
    public ResponseEntity<List<AgentResponse>> getAllAgents() {
        List<AgentResponse> agents = agentService.getAllAgents();
        return ResponseEntity.ok(agents);
    }
    
    @GetMapping("/agents/{id}")
    public ResponseEntity<AgentResponse> getAgent(@PathVariable Long id) {
        AgentResponse agent = agentService.getAgentById(id);
        return ResponseEntity.ok(agent);
    }
    
    @PutMapping("/agents/{id}/toggle-status")
    public ResponseEntity<AgentResponse> toggleAgentStatus(@PathVariable Long id) {
        AgentResponse agent = agentService.toggleAgentStatus(id);
        return ResponseEntity.ok(agent);
    }
    
    @DeleteMapping("/agents/{id}")
    public ResponseEntity<Map<String, String>> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return ResponseEntity.ok(Map.of("message", "Agent deleted successfully"));
    }
}
