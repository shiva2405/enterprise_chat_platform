package com.enterprise.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OllamaService {

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaApiUrl;

    @Value("${ollama.model:llama3.2:1b}")
    private String model;

    @Value("${rag.top-k:3}")
    private int ragTopK;

    @Value("${rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    private final VectorStoreService vectorStoreService;
    private final OllamaClient ollamaClient;

    public String generateResponse(String userMessage, String customerContext) {
        return generateResponse(userMessage, customerContext, List.of());
    }

    public String generateResponse(String userMessage, String customerContext, List<String> conversationHistory) {
        try {
            log.info("========== OLLAMA REQUEST START ==========");
            log.info("User Message: {}", userMessage);
            log.info("Customer Context: {}", customerContext);
            log.info("Conversation History Size: {}", conversationHistory.size());

            // Search for relevant context from vector store (RAG) with relevance check
            VectorStoreService.SearchResult searchResult = vectorStoreService.searchWithRelevanceCheck(
                    userMessage, ragTopK, similarityThreshold);

            log.info("Vector Store Search Results:");
            log.info("  - Is Relevant: {}", searchResult.isRelevant());
            log.info("  - Relevance Score: {}", searchResult.relevanceScore());
            log.info("  - Contexts Found: {}", searchResult.contexts().size());

            // If no relevant context found, return out-of-scope message
            if (!searchResult.isRelevant() || searchResult.contexts().isEmpty()) {
                log.info("No relevant context found for query - returning out-of-scope response");
                log.info("========== OLLAMA REQUEST END (NO CONTEXT) ==========");
                return getOutOfScopeResponse();
            }

            List<String> relevantContexts = searchResult.contexts();

            // Log the RAG contexts being used
            log.info("RAG Contexts to be used:");
            for (int i = 0; i < relevantContexts.size(); i++) {
                log.info("  [Context {}]: {}", i + 1, relevantContexts.get(i));
            }

            String prompt = buildPrompt(userMessage, customerContext, relevantContexts, conversationHistory);

            log.info("---------- FULL PROMPT SENT TO OLLAMA ----------");
            log.info("Model: {}", model);
            log.info("Prompt:\n{}", prompt);
            log.info("---------- END PROMPT ----------");

            String botResponse = ollamaClient.generateCompletion(model, prompt);

            log.info("---------- OLLAMA RESPONSE ----------");
            log.info("Raw Response: {}", botResponse);
            log.info("---------- END RESPONSE ----------");

            if (botResponse != null && !botResponse.isEmpty()) {
                // Check if response indicates out of scope
                if (botResponse.trim().equalsIgnoreCase("OUT_OF_SCOPE") ||
                    botResponse.contains("OUT_OF_SCOPE") ||
                    botResponse.toLowerCase().contains("out of scope")) {
                    log.info("Model indicated question is OUT OF SCOPE");
                    log.info("========== OLLAMA REQUEST END (OUT OF SCOPE) ==========");
                    return getOutOfScopeResponse();
                }

                String finalResponse = botResponse + " " + getAgentOfferMessage();
                log.info("Final Response to User: {}", finalResponse);
                log.info("========== OLLAMA REQUEST END (SUCCESS) ==========");
                return finalResponse;
            } else {
                log.warn("Empty response from Ollama, using fallback");
                log.info("========== OLLAMA REQUEST END (EMPTY RESPONSE) ==========");
                return getOutOfScopeResponse();
            }
        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage(), e);
            log.info("========== OLLAMA REQUEST END (ERROR) ==========");
            return getOutOfScopeResponse();
        }
    }

    private String buildPrompt(String userMessage, String customerContext,
                               List<String> relevantContexts, List<String> conversationHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a customer support assistant. Answer using ONLY the provided knowledge base context.\n\n");

        prompt.append("Instructions:\n");
        prompt.append("- If the answer appears in a table or list, extract the relevant information and summarise it.\n");
        prompt.append("- If the answer truly does not exist in the context, reply exactly: OUT_OF_SCOPE\n");
        prompt.append("- Keep responses short (1-3 sentences).\n\n");

        // Add RAG context
        prompt.append("=== KNOWLEDGE BASE CONTEXT ===\n");
        for (int i = 0; i < relevantContexts.size(); i++) {
            prompt.append(String.format("[Document %d]\n%s\n\n", i + 1, relevantContexts.get(i)));
        }
        prompt.append("=== END KNOWLEDGE BASE CONTEXT ===\n\n");

        // Add customer context
        if (customerContext != null && !customerContext.isEmpty()) {
            prompt.append("Customer context: ").append(customerContext).append("\n\n");
        }

        // Add conversation history for context
        if (!conversationHistory.isEmpty()) {
            prompt.append("=== CONVERSATION HISTORY ===\n");
            for (String message : conversationHistory) {
                prompt.append(message).append("\n");
            }
            prompt.append("=== END CONVERSATION HISTORY ===\n\n");
        }

        prompt.append("Customer message: ").append(userMessage).append("\n\n");
        prompt.append("Your response:");

        return prompt.toString();
    }

    private String getOutOfScopeResponse() {
        return "I'm sorry, but I can only answer questions related to our company's knowledge base. " +
               "This question appears to be outside my scope of assistance. " +
               getAgentOfferMessage();
    }

    private String getAgentOfferMessage() {
        return "If you'd like to speak with a human agent who may be able to help further, please type 'AGENT'.";
    }
}
