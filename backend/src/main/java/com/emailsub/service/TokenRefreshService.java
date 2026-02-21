package com.emailsub.service;

import com.emailsub.model.User;
import com.emailsub.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRefreshService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String microsoftClientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String microsoftClientSecret;

    public String getValidGmailToken(User user) {
        if (user.getGmailTokenExpiry() == null ||
            LocalDateTime.now().isAfter(user.getGmailTokenExpiry().minusMinutes(5))) {
            return refreshGmailToken(user);
        }
        return user.getGmailAccessToken();
    }

    public String getValidOutlookToken(User user) {
        if (user.getOutlookTokenExpiry() == null ||
            LocalDateTime.now().isAfter(user.getOutlookTokenExpiry().minusMinutes(5))) {
            return refreshOutlookToken(user);
        }
        return user.getOutlookAccessToken();
    }

    private String refreshGmailToken(User user) {
        try {
            WebClient client = WebClient.create("https://oauth2.googleapis.com");
            String response = client.post()
                    .uri("/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("grant_type=refresh_token" +
                               "&refresh_token=" + user.getGmailRefreshToken() +
                               "&client_id=" + googleClientId +
                               "&client_secret=" + googleClientSecret)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String newToken = json.path("access_token").asText();
            int expiresIn = json.path("expires_in").asInt(3600);

            user.setGmailAccessToken(newToken);
            user.setGmailTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
            userRepository.save(user);

            log.info("Refreshed Gmail token for user {}", user.getId());
            return newToken;
        } catch (Exception e) {
            log.error("Failed to refresh Gmail token: {}", e.getMessage());
            throw new RuntimeException("Gmail token refresh failed");
        }
    }

    private String refreshOutlookToken(User user) {
        try {
            WebClient client = WebClient.create("https://login.microsoftonline.com");
            String response = client.post()
                    .uri("/common/oauth2/v2.0/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("grant_type=refresh_token" +
                               "&refresh_token=" + user.getOutlookRefreshToken() +
                               "&client_id=" + microsoftClientId +
                               "&client_secret=" + microsoftClientSecret +
                               "&scope=Mail.Read Mail.Send offline_access")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String newToken = json.path("access_token").asText();
            int expiresIn = json.path("expires_in").asInt(3600);

            user.setOutlookAccessToken(newToken);
            user.setOutlookTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
            userRepository.save(user);

            log.info("Refreshed Outlook token for user {}", user.getId());
            return newToken;
        } catch (Exception e) {
            log.error("Failed to refresh Outlook token: {}", e.getMessage());
            throw new RuntimeException("Outlook token refresh failed");
        }
    }
}
