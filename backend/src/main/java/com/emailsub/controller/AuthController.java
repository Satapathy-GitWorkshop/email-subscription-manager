package com.emailsub.controller;

import com.emailsub.model.User;
import com.emailsub.repository.UserRepository;
import com.emailsub.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String microsoftClientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String microsoftClientSecret;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Step 1: Frontend calls this to get the OAuth URL
    @GetMapping("/oauth2/url/google")
    public ResponseEntity<Map<String, String>> getGoogleUrl() {
        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + googleClientId +
                "&redirect_uri=" + frontendUrl + "/auth/callback/google" +
                "&response_type=code" +
                "&scope=openid%20email%20profile%20https://www.googleapis.com/auth/gmail.readonly%20https://www.googleapis.com/auth/gmail.send" +
                "&access_type=offline&prompt=consent";
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/oauth2/url/microsoft")
    public ResponseEntity<Map<String, String>> getMicrosoftUrl() {
        String url = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?" +
                "client_id=" + microsoftClientId +
                "&redirect_uri=" + frontendUrl + "/auth/callback/microsoft" +
                "&response_type=code" +
                "&scope=openid%20email%20profile%20Mail.Read%20Mail.Send%20offline_access";
        return ResponseEntity.ok(Map.of("url", url));
    }

    // Step 2: Frontend sends code, backend exchanges for tokens
    @PostMapping("/oauth2/callback/google")
    public ResponseEntity<Map<String, Object>> handleGoogleCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        try {
            // Exchange code for tokens
            WebClient client = WebClient.create("https://oauth2.googleapis.com");
            String tokenResponse = client.post()
                    .uri("/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("code=" + code +
                               "&client_id=" + googleClientId +
                               "&client_secret=" + googleClientSecret +
                               "&redirect_uri=" + frontendUrl + "/auth/callback/google" +
                               "&grant_type=authorization_code")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokens = objectMapper.readTree(tokenResponse);
            String accessToken = tokens.path("access_token").asText();
            String refreshToken = tokens.path("refresh_token").asText();
            int expiresIn = tokens.path("expires_in").asInt(3600);

            // Get user info
            WebClient userClient = WebClient.create("https://www.googleapis.com");
            String userInfo = userClient.get()
                    .uri("/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode userJson = objectMapper.readTree(userInfo);
            String email = userJson.path("email").asText();
            String name = userJson.path("name").asText();
            String picture = userJson.path("picture").asText();

            // Find or create user
            User user = userRepository.findByEmail(email).orElseGet(() ->
                    User.builder().email(email).build());

            user.setName(name);
            user.setAvatarUrl(picture);
            user.setGmailConnected(true);
            user.setGmailAccessToken(accessToken);
            user.setGmailRefreshToken(refreshToken.isEmpty() ? user.getGmailRefreshToken() : refreshToken);
            user.setGmailTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
            userRepository.save(user);

            String jwt = jwtTokenProvider.generateToken(user.getId(), email);
            return ResponseEntity.ok(Map.of(
                    "token", jwt,
                    "user", Map.of("id", user.getId(), "email", email, "name", name, "avatar", picture,
                            "gmailConnected", true, "outlookConnected", user.isOutlookConnected())
            ));

        } catch (Exception e) {
            log.error("Google OAuth error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/oauth2/callback/microsoft")
    public ResponseEntity<Map<String, Object>> handleMicrosoftCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        try {
            WebClient client = WebClient.create("https://login.microsoftonline.com");
            String tokenResponse = client.post()
                    .uri("/common/oauth2/v2.0/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("code=" + code +
                               "&client_id=" + microsoftClientId +
                               "&client_secret=" + microsoftClientSecret +
                               "&redirect_uri=" + frontendUrl + "/auth/callback/microsoft" +
                               "&grant_type=authorization_code" +
                               "&scope=openid%20email%20profile%20Mail.Read%20Mail.Send%20offline_access")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokens = objectMapper.readTree(tokenResponse);
            String accessToken = tokens.path("access_token").asText();
            String refreshToken = tokens.path("refresh_token").asText();
            int expiresIn = tokens.path("expires_in").asInt(3600);

            // Get user info from Graph
            WebClient graphClient = WebClient.create("https://graph.microsoft.com/v1.0");
            String userInfo = graphClient.get()
                    .uri("/me?$select=displayName,mail,userPrincipalName")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode userJson = objectMapper.readTree(userInfo);
            String email = userJson.path("mail").asText();
            if (email.isEmpty()) email = userJson.path("userPrincipalName").asText();
            String name = userJson.path("displayName").asText();

            User user = userRepository.findByEmail(email).orElseGet(() ->
                    User.builder().email(email).build());

            user.setName(name);
            user.setOutlookConnected(true);
            user.setOutlookAccessToken(accessToken);
            user.setOutlookRefreshToken(refreshToken);
            user.setOutlookTokenExpiry(LocalDateTime.now().plusSeconds(expiresIn));
            userRepository.save(user);

            String jwt = jwtTokenProvider.generateToken(user.getId(), email);
            return ResponseEntity.ok(Map.of(
                    "token", jwt,
                    "user", Map.of("id", user.getId(), "email", email, "name", name,
                            "gmailConnected", user.isGmailConnected(), "outlookConnected", true)
            ));

        } catch (Exception e) {
            log.error("Microsoft OAuth error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(
            @RequestHeader("Authorization") String authHeader) {
        // Extract user from JWT (already authenticated via filter)
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : "",
                "avatar", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "gmailConnected", user.isGmailConnected(),
                "outlookConnected", user.isOutlookConnected()
        ));
    }
}
