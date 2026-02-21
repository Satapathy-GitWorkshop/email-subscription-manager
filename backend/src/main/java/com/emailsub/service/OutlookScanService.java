package com.emailsub.service;

import com.emailsub.model.*;
import com.emailsub.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutlookScanService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final CommunitySenderRepository communitySenderRepository;
    private final SyncLogRepository syncLogRepository;
    private final AiCategorizationService categorizationService;
    private final TokenRefreshService tokenRefreshService;
    private final ObjectMapper objectMapper;

    private static final String GRAPH_API = "https://graph.microsoft.com/v1.0";
    private static final Pattern UNSUBSCRIBE_URL_PATTERN =
            Pattern.compile("<(https?://[^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSUBSCRIBE_MAILTO_PATTERN =
            Pattern.compile("<mailto:([^>]+)>", Pattern.CASE_INSENSITIVE);

    public Map<String, Object> scanInbox(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isOutlookConnected()) {
            return Map.of("error", "Outlook not connected");
        }

        SyncLog syncLog = SyncLog.builder()
                .user(user)
                .accountType("outlook")
                .syncType(user.getOutlookDeltaToken() == null ? "full" : "delta")
                .status("running")
                .build();
        syncLogRepository.save(syncLog);

        long startTime = System.currentTimeMillis();
        int emailsScanned = 0;
        int newSenders = 0;

        try {
            String accessToken = tokenRefreshService.getValidOutlookToken(user);

            WebClient client = WebClient.builder()
                    .baseUrl(GRAPH_API)
                    .defaultHeader("Authorization", "Bearer " + accessToken)
                    .build();

            Map<String, GmailScanService.SubscriptionData> senderMap = new HashMap<>();
            String nextLink = buildInitialUrl(user.getOutlookDeltaToken());
            String newDeltaToken = null;

            while (nextLink != null) {
                String response = client.get().uri(nextLink).retrieve().bodyToMono(String.class).block();
                JsonNode root = objectMapper.readTree(response);

                // Check for delta token in response
                JsonNode deltaLink = root.path("@odata.deltaLink");
                if (!deltaLink.isMissingNode()) {
                    newDeltaToken = deltaLink.asText();
                }

                JsonNode nextLinkNode = root.path("@odata.nextLink");
                nextLink = nextLinkNode.isMissingNode() ? null : nextLinkNode.asText();

                JsonNode messages = root.path("value");
                if (messages.isArray()) {
                    for (JsonNode msg : messages) {
                        processMessage(msg, senderMap);
                        emailsScanned++;
                    }
                }
            }

            for (Map.Entry<String, GmailScanService.SubscriptionData> entry : senderMap.entrySet()) {
                if (saveOrUpdateSubscription(user, entry.getValue(), "outlook")) {
                    newSenders++;
                }
            }

            if (newDeltaToken != null) {
                user.setOutlookDeltaToken(newDeltaToken);
                user.setOutlookLastSync(LocalDateTime.now());
                userRepository.save(user);
            }

            syncLog.setStatus("success");
            syncLog.setEmailsScanned(emailsScanned);
            syncLog.setNewSendersFound(newSenders);
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            return Map.of("success", true, "emailsScanned", emailsScanned, "newSenders", newSenders);

        } catch (Exception e) {
            log.error("Outlook scan failed for user {}: {}", userId, e.getMessage());
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setCompletedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);
            return Map.of("error", e.getMessage());
        }
    }

    private String buildInitialUrl(String deltaToken) {
        if (deltaToken != null && !deltaToken.isEmpty()) {
            return deltaToken; // Delta token IS the full URL for next sync
        }
        // First time scan - get inbox messages with internet headers
        return "/me/mailFolders/inbox/messages/delta?" +
               "$select=sender,subject,receivedDateTime,internetMessageHeaders&$top=100";
    }

    private void processMessage(JsonNode msg, Map<String, GmailScanService.SubscriptionData> senderMap) {
        try {
            String unsubscribeHeader = null;
            String unsubscribePost = null;

            JsonNode headers = msg.path("internetMessageHeaders");
            if (headers.isArray()) {
                for (JsonNode h : headers) {
                    String name = h.path("name").asText().toLowerCase();
                    if (name.equals("list-unsubscribe")) unsubscribeHeader = h.path("value").asText();
                    if (name.equals("list-unsubscribe-post")) unsubscribePost = h.path("value").asText();
                }
            }

            if (unsubscribeHeader == null) return;

            String senderEmail = msg.path("sender").path("emailAddress").path("address").asText();
            String senderName = msg.path("sender").path("emailAddress").path("name").asText();
            String subject = msg.path("subject").asText();

            if (senderEmail == null || senderEmail.isEmpty()) return;

            String domain = senderEmail.contains("@") ?
                    senderEmail.substring(senderEmail.indexOf("@") + 1).toLowerCase() : null;
            if (domain == null) return;

            String unsubUrl = extractUrl(unsubscribeHeader);
            String unsubMailto = extractMailto(unsubscribeHeader);
            String unsubType = (unsubscribePost != null && unsubscribePost.contains("One-Click"))
                    ? "one-click" : (unsubUrl != null ? "link" : "mailto");

            GmailScanService.SubscriptionData data = senderMap.computeIfAbsent(
                    senderEmail, k -> new GmailScanService.SubscriptionData());
            data.senderEmail = senderEmail.toLowerCase();
            data.senderName = senderName;
            data.domain = domain;
            data.unsubscribeUrl = unsubUrl;
            data.unsubscribeMailto = unsubMailto;
            data.unsubscribeType = unsubType;
            data.emailCount++;
            if (subject != null && !subject.isEmpty()) data.subjects.add(subject);

        } catch (Exception e) {
            log.debug("Error processing Outlook message: {}", e.getMessage());
        }
    }

    private boolean saveOrUpdateSubscription(User user, GmailScanService.SubscriptionData data, String accountType) {
        boolean isNew = false;
        Optional<UserSubscription> existing = subscriptionRepository
                .findByUserIdAndSenderEmailAndAccountType(user.getId(), data.senderEmail, accountType);

        UserSubscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
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

        communitySenderRepository.findByDomain(data.domain)
                .ifPresentOrElse(
                        sub::setCommunitySender,
                        () -> categorizationService.addToQueue(
                                data.domain, data.senderName, data.senderEmail,
                                new ArrayList<>(data.subjects))
                );

        subscriptionRepository.save(sub);
        return isNew;
    }

    private String extractUrl(String header) {
        Matcher m = UNSUBSCRIBE_URL_PATTERN.matcher(header);
        return m.find() ? m.group(1) : null;
    }

    private String extractMailto(String header) {
        Matcher m = UNSUBSCRIBE_MAILTO_PATTERN.matcher(header);
        return m.find() ? m.group(1) : null;
    }
}
