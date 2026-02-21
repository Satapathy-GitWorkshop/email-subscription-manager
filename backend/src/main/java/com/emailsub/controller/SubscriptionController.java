package com.emailsub.controller;

import com.emailsub.model.User;
import com.emailsub.service.GmailScanService;
import com.emailsub.service.OutlookScanService;
import com.emailsub.service.SubscriptionService;
import com.emailsub.service.UnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final GmailScanService gmailScanService;
    private final OutlookScanService outlookScanService;
    private final UnsubscribeService unsubscribeService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionService.getDashboard(user.getId()));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Map<String, Object>> getByCategory(
            @AuthenticationPrincipal User user,
            @PathVariable String category) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionsByCategory(user.getId(), category));
    }

    @PostMapping("/scan/gmail")
    public ResponseEntity<Map<String, Object>> scanGmail(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(gmailScanService.scanInbox(user.getId()));
    }

    @PostMapping("/scan/outlook")
    public ResponseEntity<Map<String, Object>> scanOutlook(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(outlookScanService.scanInbox(user.getId()));
    }

    @PostMapping("/scan/all")
    public ResponseEntity<Map<String, Object>> scanAll(@AuthenticationPrincipal User user) {
        Map<String, Object> gmailResult = user.isGmailConnected()
                ? gmailScanService.scanInbox(user.getId()) : Map.of("skipped", true);
        Map<String, Object> outlookResult = user.isOutlookConnected()
                ? outlookScanService.scanInbox(user.getId()) : Map.of("skipped", true);
        return ResponseEntity.ok(Map.of("gmail", gmailResult, "outlook", outlookResult));
    }

    @PostMapping("/{id}/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(unsubscribeService.unsubscribe(user.getId(), id));
    }

    @PatchMapping("/{id}/category")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                subscriptionService.correctCategory(user.getId(), id, body.get("category"))
        );
    }
}
