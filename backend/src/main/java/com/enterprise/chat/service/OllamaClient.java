package com.enterprise.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@Slf4j
public class OllamaClient {

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateCompletion(String model, String prompt) {
        try {
            String apiUrl = ollamaApiUrl + "/api/generate";

            log.debug("Calling Ollama API: {}", apiUrl);
            log.debug("Model: {}", model);
            log.debug("Prompt length: {} characters", prompt.length());

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            OllamaResponse response = restTemplate.postForObject(apiUrl, request, OllamaResponse.class);
            long duration = System.currentTimeMillis() - startTime;

            log.info("Ollama API call completed in {} ms", duration);

            if (response != null) {
                log.info("Ollama Response Details:");
                log.info("  - Model: {}", response.model());
                log.info("  - Done: {}", response.done());
                log.info("  - Total Duration: {} ms", response.total_duration() != null ? response.total_duration() / 1_000_000 : "N/A");
                log.info("  - Prompt Eval Count: {}", response.prompt_eval_count());
                log.info("  - Eval Count: {}", response.eval_count());
                log.info("  - Response Length: {} characters", response.response() != null ? response.response().length() : 0);

                if (response.response() != null) {
                    return response.response().trim();
                }
            }

            log.warn("Ollama returned null or empty response");
            return null;
        } catch (Exception e) {
            log.error("Error calling Ollama API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Ollama API", e);
        }
    }

    public record OllamaResponse(
            String model,
            String response,
            boolean done,
            Long total_duration,
            Long load_duration,
            Integer prompt_eval_count,
            Integer eval_count
    ) {}
}
