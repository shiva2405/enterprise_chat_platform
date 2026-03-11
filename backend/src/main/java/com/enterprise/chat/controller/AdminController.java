package com.enterprise.chat.controller;

import com.enterprise.chat.dto.AgentResponse;
import com.enterprise.chat.dto.CreateAgentRequest;
import com.enterprise.chat.dto.KnowledgeDocumentDTO;
import com.enterprise.chat.model.KnowledgeDocument;
import com.enterprise.chat.service.AgentService;
import com.enterprise.chat.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    
    private final AgentService agentService;
    private final DocumentService documentService;
    
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

    // Knowledge Document Endpoints

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) throws IOException {
        KnowledgeDocument document = documentService.uploadDocument(file, description);
        return ResponseEntity.ok(KnowledgeDocumentDTO.fromEntity(document));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentDTO>> getAllDocuments() {
        List<KnowledgeDocumentDTO> documents = documentService.getAllDocuments().stream()
                .map(KnowledgeDocumentDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocumentDTO> getDocument(@PathVariable Long id) {
        KnowledgeDocument document = documentService.getDocument(id);
        return ResponseEntity.ok(KnowledgeDocumentDTO.fromEntity(document));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }
}
