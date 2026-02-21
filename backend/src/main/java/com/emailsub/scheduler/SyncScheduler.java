package com.emailsub.scheduler;

import com.emailsub.model.User;
import com.emailsub.repository.UserRepository;
import com.emailsub.service.GmailScanService;
import com.emailsub.service.OutlookScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final UserRepository userRepository;
    private final GmailScanService gmailScanService;
    private final OutlookScanService outlookScanService;

    @Value("${app.sync.interval-hours}")
    private int syncIntervalHours;

    @Scheduled(fixedDelay = 3600000) // every hour, check who needs syncing
    public void scheduledSync() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(syncIntervalHours);
        List<User> users = userRepository.findAll();

        for (User user : users) {
            try {
                if (user.isGmailConnected()) {
                    boolean needsSync = user.getGmailLastSync() == null ||
                                       user.getGmailLastSync().isBefore(threshold);
                    if (needsSync) {
                        log.info("Background Gmail sync for user {}", user.getId());
                        gmailScanService.scanInbox(user.getId());
                    }
                }

                if (user.isOutlookConnected()) {
                    boolean needsSync = user.getOutlookLastSync() == null ||
                                       user.getOutlookLastSync().isBefore(threshold);
                    if (needsSync) {
                        log.info("Background Outlook sync for user {}", user.getId());
                        outlookScanService.scanInbox(user.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Background sync failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }
}
