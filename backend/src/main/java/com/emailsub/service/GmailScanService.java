package com.emailsub.service;

import com.emailsub.model.*;
import com.emailsub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailScanService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final CommunitySenderRepository communitySenderRepository;
    private final SyncLogRepository syncLogRepository;
    private final AiCategorizationService categorizationService;
    private final TokenRefreshService tokenRefreshService;
    private final ObjectMapper objectMapper;

    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1";
    private static final Pattern UNSUBSCRIBE_URL_PATTERN =
            Pattern.compile("<(https?://[^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSUBSCRIBE_MAILTO_PATTERN =
            Pattern.compile("<mailto:([^>]+)>", Pattern.CASE_INSENSITIVE);

    public Map<String, Object> scanInbox(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isGmailConnected()) {
            return Map.of("error", "Gmail not connected");
        }

        SyncLog syncLog = SyncLog.builder()
                .user(user)
                .accountType("gmail")
                .syncType(user.getGmailSyncToken() == null ? "full" : "delta")
                .status("running")
                .build();
        syncLogRepository.save(syncLog);

        long startTime = System.currentTimeMillis();
        int emailsScanned = 0;
        int newSenders = 0;

        try {
            // Refresh token if needed
            String accessToken = tokenRefreshService.getValidGmailToken(user);

            WebClient client = WebClient.builder()
                    .baseUrl(GMAIL_API)
                    .defaultHeader("Authorization", "Bearer " + accessToken)
                    .build();

            // Fetch message list with sync token if available
            String nextPageToken = null;
            String newSyncToken = null;
            Map<String, SubscriptionData> senderMap = new HashMap<>();

            do {
                String url = buildListUrl(user.getGmailSyncToken(), nextPageToken);
                String response = client.get().uri(url).retrieve().bodyToMono(String.class).block();
                JsonNode root = objectMapper.readTree(response);

                newSyncToken = root.path("nextSyncToken").asText(null);
                nextPageToken = root.path("nextPageToken").asText(null);

                JsonNode messages = root.path("messages");
                if (messages.isArray()) {
                    for (JsonNode msg : messages) {
                        String msgId = msg.path("id").asText();
                        processMessage(client, msgId, senderMap);
                        emailsScanned++;
                    }
                }

                if ("".equals(nextPageToken)) nextPageToken = null;

            } while (nextPageToken != null);

            // Save subscriptions to DB
            for (Map.Entry<String, SubscriptionData> entry : senderMap.entrySet()) {
                if (saveOrUpdateSubscription(user, entry.getValue(), "gmail")) {
                    newSenders++;
                }
            }

            // Save new sync token
            if (newSyncToken != null && !newSyncToken.isEmpty()) {
                user.setGmailSyncToken(newSyncToken);
                user.setGmailLastSync(LocalDateTime.now());
                userRepository.save(user);
            }

            syncLog.setStatus("success");
            syncLog.setEmailsScanned(emailsScanned);
            syncLog.setNewSendersFound(newSenders);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            return Map.of(
                    "success", true,
                    "emailsScanned", emailsScanned,
                    "newSenders", newSenders,
                    "syncType", syncLog.getSyncType()
            );

        } catch (Exception e) {
            log.error("Gmail scan failed for user {}: {}", userId, e.getMessage());
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
            return Map.of("error", e.getMessage());
        }
    }

    private String buildListUrl(String syncToken, String pageToken) {
        StringBuilder url = new StringBuilder("/users/me/messages?maxResults=100");
        // Only scan Inbox, Promotions, Updates - skip Spam, Trash, Sent
        url.append("&labelIds=INBOX&labelIds=CATEGORY_PROMOTIONS&labelIds=CATEGORY_UPDATES");
        if (syncToken != null && !syncToken.isEmpty()) {
            url.append("&syncToken=").append(syncToken);
        }
        if (pageToken != null && !pageToken.isEmpty()) {
            url.append("&pageToken=").append(pageToken);
        }
        return url.toString();
    }

    private void processMessage(WebClient client, String msgId, Map<String, SubscriptionData> senderMap) {
        try {
            String response = client.get()
                    .uri("/users/me/messages/" + msgId + "?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date&metadataHeaders=List-Unsubscribe&metadataHeaders=List-Unsubscribe-Post")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode msg = objectMapper.readTree(response);
            JsonNode headers = msg.path("payload").path("headers");

            String from = null, subject = null, date = null, unsubscribeHeader = null, unsubscribePost = null;

            for (JsonNode header : headers) {
                String name = header.path("name").asText();
                String value = header.path("value").asText();
                switch (name.toLowerCase()) {
                    case "from" -> from = value;
                    case "subject" -> subject = value;
                    case "date" -> date = value;
                    case "list-unsubscribe" -> unsubscribeHeader = value;
                    case "list-unsubscribe-post" -> unsubscribePost = value;
                }
            }

            // Only process emails with unsubscribe header
            if (unsubscribeHeader == null || unsubscribeHeader.isEmpty()) return;

            String senderEmail = extractEmail(from);
            String senderName = extractName(from);
            if (senderEmail == null) return;

            String domain = extractDomain(senderEmail);
            String unsubscribeUrl = extractUrl(unsubscribeHeader);
            String unsubscribeMailto = extractMailto(unsubscribeHeader);
            String unsubType = determineUnsubscribeType(unsubscribePost, unsubscribeUrl);

            SubscriptionData data = senderMap.computeIfAbsent(senderEmail, k -> new SubscriptionData());
            data.senderEmail = senderEmail;
            data.senderName = senderName;
            data.domain = domain;
            data.unsubscribeUrl = unsubscribeUrl;
            data.unsubscribeMailto = unsubscribeMailto;
            data.unsubscribeType = unsubType;
            data.emailCount++;
            if (subject != null) data.subjects.add(subject);

        } catch (Exception e) {
            log.debug("Error processing message {}: {}", msgId, e.getMessage());
        }
    }

    private boolean saveOrUpdateSubscription(User user, SubscriptionData data, String accountType) {
        boolean isNew = false;
        Optional<UserSubscription> existing = subscriptionRepository
                .findByUserIdAndSenderEmailAndAccountType(user.getId(), data.senderEmail, accountType);

        UserSubscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
            sub.setTotalEmailCount(sub.getTotalEmailCount() + data.emailCount);
            sub.setEmailCount30days(data.emailCount);
        } else {
            sub = new UserSubscription();
            sub.setUser(user);
            sub.setSenderEmail(data.senderEmail);
            sub.setSenderName(data.senderName);
            sub.setAccountType(accountType);
            sub.setTotalEmailCount(data.emailCount);
            sub.setEmailCount30days(data.emailCount);
            sub.setFirstEmailAt(LocalDateTime.now());
            isNew = true;
        }

        sub.setUnsubscribeLink(data.unsubscribeUrl);
        sub.setUnsubscribeMailto(data.unsubscribeMailto);
        sub.setUnsubscribeType(data.unsubscribeType);
        sub.setLastEmailAt(LocalDateTime.now());

        // Link to community sender if exists
        communitySenderRepository.findByDomain(data.domain)
                .ifPresentOrElse(
                        sub::setCommunitySender,
                        () -> categorizationService.addToQueue(
                                data.domain, data.senderName, data.senderEmail,
                                new ArrayList<>(data.subjects)
                        )
                );

        subscriptionRepository.save(sub);
        return isNew;
    }

    private String extractEmail(String from) {
        if (from == null) return null;
        Pattern p = Pattern.compile("<([^>]+@[^>]+)>");
        Matcher m = p.matcher(from);
        if (m.find()) return m.group(1).toLowerCase();
        if (from.contains("@")) return from.trim().toLowerCase();
        return null;
    }

    private String extractName(String from) {
        if (from == null) return null;
        if (from.contains("<")) return from.substring(0, from.indexOf("<")).trim().replaceAll("[\"']", "");
        return from.trim();
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) return null;
        return email.substring(email.indexOf("@") + 1).toLowerCase();
    }

    private String extractUrl(String header) {
        Matcher m = UNSUBSCRIBE_URL_PATTERN.matcher(header);
        return m.find() ? m.group(1) : null;
    }

    private String extractMailto(String header) {
        Matcher m = UNSUBSCRIBE_MAILTO_PATTERN.matcher(header);
        return m.find() ? m.group(1) : null;
    }

    private String determineUnsubscribeType(String postHeader, String url) {
        if (postHeader != null && postHeader.contains("One-Click")) return "one-click";
        if (url != null) return "link";
        return "mailto";
    }

    // Inner class to hold aggregated sender data during scan
    static class SubscriptionData {
        String senderEmail;
        String senderName;
        String domain;
        String unsubscribeUrl;
        String unsubscribeMailto;
        String unsubscribeType;
        int emailCount = 0;
        Set<String> subjects = new LinkedHashSet<>();
    }
}
