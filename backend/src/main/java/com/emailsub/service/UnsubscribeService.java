package com.emailsub.service;

import com.emailsub.model.User;
import com.emailsub.model.UserSubscription;
import com.emailsub.repository.UserRepository;
import com.emailsub.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnsubscribeService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TokenRefreshService tokenRefreshService;

    public Map<String, Object> unsubscribe(UUID userId, UUID subscriptionId) {
        UserSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (!sub.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String result;
        String method;

        try {
            if ("one-click".equals(sub.getUnsubscribeType()) && sub.getUnsubscribeLink() != null) {
                result = handleOneClick(sub.getUnsubscribeLink());
                method = "one-click";
            } else if (sub.getUnsubscribeMailto() != null) {
                result = handleMailto(user, sub);
                method = "mailto";
            } else if (sub.getUnsubscribeLink() != null) {
                // Return link for manual handling
                sub.setStatus("pending");
                subscriptionRepository.save(sub);
                return Map.of(
                        "success", true,
                        "method", "manual",
                        "url", sub.getUnsubscribeLink(),
                        "message", "Please visit this URL to complete unsubscribe"
                );
            } else {
                return Map.of("error", "No unsubscribe method available");
            }

            sub.setStatus("unsubscribed");
            sub.setUnsubscribedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);

            return Map.of("success", true, "method", method, "message", result);

        } catch (Exception e) {
            log.error("Unsubscribe failed for sub {}: {}", subscriptionId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private String handleOneClick(String url) {
        try {
            WebClient client = WebClient.create();
            client.post()
                    .uri(url)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("List-Unsubscribe=One-Click")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return "One-click unsubscribe successful";
        } catch (Exception e) {
            log.warn("One-click failed, URL: {}", url);
            throw new RuntimeException("One-click unsubscribe failed: " + e.getMessage());
        }
    }

    private String handleMailto(User user, UserSubscription sub) {
        try {
            String accessToken;
            if ("gmail".equals(sub.getAccountType())) {
                accessToken = tokenRefreshService.getValidGmailToken(user);
                sendGmailUnsubscribe(accessToken, sub.getUnsubscribeMailto(), user.getEmail());
            } else {
                accessToken = tokenRefreshService.getValidOutlookToken(user);
                sendOutlookUnsubscribe(accessToken, sub.getUnsubscribeMailto(), user.getEmail());
            }
            return "Unsubscribe email sent";
        } catch (Exception e) {
            throw new RuntimeException("Mailto unsubscribe failed: " + e.getMessage());
        }
    }

    private void sendGmailUnsubscribe(String accessToken, String to, String from) {
        String rawEmail = "From: " + from + "\r\n" +
                          "To: " + to + "\r\n" +
                          "Subject: Unsubscribe\r\n\r\n" +
                          "Please unsubscribe me from this mailing list.";

        String encoded = java.util.Base64.getUrlEncoder()
                .encodeToString(rawEmail.getBytes());

        WebClient client = WebClient.builder()
                .baseUrl("https://gmail.googleapis.com/gmail/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.post()
                .uri("/users/me/messages/send")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("raw", encoded))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void sendOutlookUnsubscribe(String accessToken, String to, String from) {
        Map<String, Object> message = Map.of(
                "subject", "Unsubscribe",
                "body", Map.of("contentType", "Text", "content", "Please unsubscribe me."),
                "toRecipients", java.util.List.of(
                        Map.of("emailAddress", Map.of("address", to))
                )
        );

        WebClient client = WebClient.builder()
                .baseUrl("https://graph.microsoft.com/v1.0")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.post()
                .uri("/me/sendMail")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("message", message))
                .retrieve()
                .bodyToMono(Void.class)
                .block();
    }
}
