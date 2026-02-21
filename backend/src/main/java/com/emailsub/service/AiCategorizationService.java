package com.emailsub.service;

import com.emailsub.model.CategorizationQueue;
import com.emailsub.model.CommunitySender;
import com.emailsub.repository.CategorizationQueueRepository;
import com.emailsub.repository.CommunitySenderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCategorizationService {

    private final CategorizationQueueRepository queueRepository;
    private final CommunitySenderRepository communitySenderRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.groq.api-key}")
    private String groqApiKey;

    @Value("${app.ai.groq.base-url}")
    private String groqBaseUrl;

    @Value("${app.ai.groq.model}")
    private String groqModel;

    @Value("${app.ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${app.ai.cloudflare.api-key}")
    private String cloudflareApiKey;

    @Value("${app.ai.cloudflare.account-id}")
    private String cloudflareAccountId;

    @Value("${app.ai.cloudflare.base-url}")
    private String cloudflareBaseUrl;

    private static final List<String> VALID_CATEGORIES = Arrays.asList(
            "Jobs", "Finance", "Shopping", "Learning", "News",
            "Social", "Travel", "Health", "Entertainment", "Other"
    );

    @Scheduled(fixedDelayString = "${app.categorization.queue-process-delay-ms}")
    public void processQueue() {
        List<CategorizationQueue> pendingItems = queueRepository.findPendingItems();
        if (pendingItems.isEmpty()) return;

        // Process one at a time to respect rate limits
        CategorizationQueue item = pendingItems.get(0);
        processItem(item);
    }

    private void processItem(CategorizationQueue item) {
        item.setStatus("processing");
        item.setAttempts(item.getAttempts() + 1);
        queueRepository.save(item);

        try {
            String prompt = buildPrompt(item);
            String category = null;
            String provider = null;

            // Try Groq first
            try {
                category = callGroq(prompt);
                provider = "groq";
                log.info("Groq categorized {} as {}", item.getDomain(), category);
            } catch (Exception e) {
                log.warn("Groq failed for {}: {}", item.getDomain(), e.getMessage());
            }

            // Fallback to Gemini
            if (category == null) {
                try {
                    category = callGemini(prompt);
                    provider = "gemini";
                    log.info("Gemini categorized {} as {}", item.getDomain(), category);
                } catch (Exception e) {
                    log.warn("Gemini failed for {}: {}", item.getDomain(), e.getMessage());
                }
            }

            // Fallback to Cloudflare
            if (category == null) {
                try {
                    category = callCloudflare(prompt);
                    provider = "cloudflare";
                    log.info("Cloudflare categorized {} as {}", item.getDomain(), category);
                } catch (Exception e) {
                    log.warn("Cloudflare failed for {}: {}", item.getDomain(), e.getMessage());
                }
            }

            if (category != null && isValidCategory(category)) {
                saveToCommunityDB(item, category, provider);
                item.setStatus("done");
                item.setAssignedCategory(category);
                item.setAiProvider(provider);
                item.setProcessedAt(LocalDateTime.now());
            } else {
                // All providers failed or returned invalid category
                if (item.getAttempts() >= item.getMaxAttempts()) {
                    item.setStatus("failed");
                    item.setAssignedCategory("Other");
                    saveToCommunityDB(item, "Other", "fallback");
                } else {
                    item.setStatus("pending"); // retry later
                }
                item.setErrorMessage("All AI providers failed or returned invalid category");
            }
        } catch (Exception e) {
            log.error("Error processing queue item {}: {}", item.getId(), e.getMessage());
            item.setStatus(item.getAttempts() >= item.getMaxAttempts() ? "failed" : "pending");
            item.setErrorMessage(e.getMessage());
        }

        queueRepository.save(item);
    }

    private String buildPrompt(CategorizationQueue item) {
        return String.format(
            "Categorize this email sender into exactly one category.\n\n" +
            "Sender Name: %s\n" +
            "Domain: %s\n" +
            "Recent Email Subjects: %s\n\n" +
            "Available categories: Jobs, Finance, Shopping, Learning, News, Social, Travel, Health, Entertainment, Other\n\n" +
            "Reply with ONLY the category name, nothing else.",
            item.getSenderName(),
            item.getDomain(),
            item.getSampleSubjects() != null ? item.getSampleSubjects() : "Not available"
        );
    }

    private String callGroq(String prompt) {
        WebClient client = WebClient.builder()
                .baseUrl(groqBaseUrl)
                .defaultHeader("Authorization", "Bearer " + groqApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> body = Map.of(
                "model", groqModel,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", 20,
                "temperature", 0.1
        );

        String response = client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractGroqResponse(response);
    }

    private String callGemini(String prompt) {
        WebClient client = WebClient.builder()
                .baseUrl(geminiBaseUrl)
                .build();

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        String response = client.post()
                .uri("/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractGeminiResponse(response);
    }

    private String callCloudflare(String prompt) {
        WebClient client = WebClient.builder()
                .baseUrl(cloudflareBaseUrl)
                .defaultHeader("Authorization", "Bearer " + cloudflareApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String response = client.post()
                .uri("/" + cloudflareAccountId + "/ai/run/@cf/meta/llama-3-8b-instruct")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractCloudflareResponse(response);
    }

    private String extractGroqResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return cleanCategory(root.path("choices").get(0)
                    .path("message").path("content").asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractGeminiResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return cleanCategory(root.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text").asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCloudflareResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return cleanCategory(root.path("result").path("response").asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanCategory(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("[^a-zA-Z]", "");
    }

    private boolean isValidCategory(String category) {
        return VALID_CATEGORIES.stream()
                .anyMatch(c -> c.equalsIgnoreCase(category));
    }

    private String normalizeCategory(String category) {
        return VALID_CATEGORIES.stream()
                .filter(c -> c.equalsIgnoreCase(category))
                .findFirst()
                .orElse("Other");
    }

    private void saveToCommunityDB(CategorizationQueue item, String category, String provider) {
        if (communitySenderRepository.existsByDomain(item.getDomain())) return;

        CommunitySender sender = CommunitySender.builder()
                .domain(item.getDomain())
                .senderName(item.getSenderName() != null ? item.getSenderName() : item.getDomain())
                .category(normalizeCategory(category))
                .confidenceScore(new BigDecimal("70.00"))
                .categorizedBy(provider)
                .isTrusted(false)
                .isSpam(false)
                .build();

        communitySenderRepository.save(sender);
        log.info("Saved {} -> {} to community DB", item.getDomain(), category);
    }

    public void addToQueue(String domain, String senderName, String senderEmail, List<String> subjects) {
        // Don't add if already in community DB
        if (communitySenderRepository.existsByDomain(domain)) return;

        // Don't add if already in queue
        if (queueRepository.existsByDomainAndStatusIn(domain, List.of("pending", "processing", "done"))) return;

        String subjectsJson = "[" + subjects.stream()
                .limit(3)
                .map(s -> "\"" + s.replace("\"", "'") + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";

        CategorizationQueue item = CategorizationQueue.builder()
                .domain(domain)
                .senderName(senderName)
                .senderEmail(senderEmail)
                .sampleSubjects(subjectsJson)
                .status("pending")
                .priority(5)
                .attempts(0)
                .maxAttempts(3)
                .build();

        queueRepository.save(item);
        log.info("Added {} to categorization queue", domain);
    }
}
