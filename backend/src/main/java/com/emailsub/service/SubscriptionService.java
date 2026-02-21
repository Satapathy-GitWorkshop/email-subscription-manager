package com.emailsub.service;

import com.emailsub.model.CommunitySender;
import com.emailsub.model.UserCorrection;
import com.emailsub.model.UserSubscription;
import com.emailsub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final CommunitySenderRepository communitySenderRepository;
    private final UserCorrectionRepository correctionRepository;

    public Map<String, Object> getDashboard(UUID userId) {
        List<UserSubscription> subs = subscriptionRepository.findByUserIdWithSender(userId);

        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        long activeCount = 0;
        long unsubscribedCount = 0;

        // Define category order
        List<String> categoryOrder = List.of(
                "Jobs", "Finance", "Shopping", "Learning", "News",
                "Social", "Travel", "Health", "Entertainment", "Other"
        );
        categoryOrder.forEach(c -> byCategory.put(c, new ArrayList<>()));

        for (UserSubscription sub : subs) {
            String category = sub.getEffectiveCategory();
            if (!byCategory.containsKey(category)) byCategory.put(category, new ArrayList<>());

            byCategory.get(category).add(toMap(sub));

            if ("active".equals(sub.getStatus())) activeCount++;
            else if ("unsubscribed".equals(sub.getStatus())) unsubscribedCount++;
        }

        // Remove empty categories
        byCategory.entrySet().removeIf(e -> e.getValue().isEmpty());

        return Map.of(
                "categories", byCategory,
                "totalActive", activeCount,
                "totalUnsubscribed", unsubscribedCount,
                "totalSenders", subs.size()
        );
    }

    public Map<String, Object> getSubscriptionsByCategory(UUID userId, String category) {
        List<UserSubscription> subs = subscriptionRepository.findByUserIdAndCategory(userId, category);
        return Map.of(
                "category", category,
                "subscriptions", subs.stream().map(this::toMap).collect(Collectors.toList())
        );
    }

    public Map<String, Object> correctCategory(UUID userId, UUID subscriptionId, String newCategory) {
        UserSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (!sub.getUser().getId().equals(userId)) throw new RuntimeException("Unauthorized");

        String oldCategory = sub.getEffectiveCategory();
        sub.setCustomCategory(newCategory);
        subscriptionRepository.save(sub);

        // Save correction to community DB
        if (sub.getCommunitySender() != null) {
            CommunitySender sender = sub.getCommunitySender();

            UserCorrection correction = UserCorrection.builder()
                    .user(sub.getUser())
                    .communitySender(sender)
                    .originalCategory(oldCategory)
                    .correctedCategory(newCategory)
                    .build();
            correctionRepository.save(correction);

            // If 10+ corrections all agree → update community DB
            long totalCorrections = correctionRepository.countByCommunitySenderId(sender.getId());
            if (totalCorrections >= 10) {
                List<Object[]> topCorrection = correctionRepository.findTopCorrectionForSender(sender.getId());
                if (!topCorrection.isEmpty()) {
                    String topCategory = (String) topCorrection.get(0)[0];
                    long topCount = (Long) topCorrection.get(0)[1];
                    double agreement = (double) topCount / totalCorrections;

                    if (agreement >= 0.7) {
                        sender.setCategory(topCategory);
                        sender.setCorrectionCount((int) totalCorrections);
                        sender.setConfidenceScore(BigDecimal.valueOf(agreement * 100));
                        communitySenderRepository.save(sender);
                        log.info("Community DB updated: {} -> {}", sender.getDomain(), topCategory);
                    }
                }
            }
        }

        return Map.of("success", true, "newCategory", newCategory);
    }

    private Map<String, Object> toMap(UserSubscription sub) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", sub.getId());
        map.put("senderEmail", sub.getSenderEmail());
        map.put("senderName", sub.getSenderName());
        map.put("emailCount30days", sub.getEmailCount30days());
        map.put("totalEmailCount", sub.getTotalEmailCount());
        map.put("status", sub.getStatus());
        map.put("accountType", sub.getAccountType());
        map.put("category", sub.getEffectiveCategory());
        map.put("unsubscribeType", sub.getUnsubscribeType());
        map.put("hasUnsubscribeLink", sub.getUnsubscribeLink() != null || sub.getUnsubscribeMailto() != null);
        map.put("lastEmailAt", sub.getLastEmailAt());
        map.put("unsubscribedAt", sub.getUnsubscribedAt());

        String frequency;
        int count = sub.getEmailCount30days();
        if (count > 20) frequency = "20+ emails recently";
        else if (count >= 10) frequency = "10-20 emails recently";
        else frequency = "10 or fewer emails recently";
        map.put("frequency", frequency);

        return map;
    }
}
