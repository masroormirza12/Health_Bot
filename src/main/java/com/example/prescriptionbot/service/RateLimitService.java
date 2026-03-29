package com.example.prescriptionbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter per user ID.
 * While a user has an active request, subsequent requests are blocked.
 * Includes a self-healing mechanism to release users after a timeout.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final long REQUEST_TIMEOUT_SECONDS = 300; // 5 minutes

    // Users currently processing a request (busy) with timestamp
    private final Map<String, Instant> activeUsers = new ConcurrentHashMap<>();

    // Permanently blocked users (admin-managed)
    private final Set<String> blockedUsers = ConcurrentHashMap.newKeySet();

    /**
     * Attempts to acquire a slot for the user. Returns true if acquired, false if
     * busy or blocked.
     */
    public boolean tryAcquire(String userId) {
        if (blockedUsers.contains(userId)) {
            log.warn("User {} is blocked, rejecting request", userId);
            return false;
        }

        // atomicity: putIfAbsent returns null if the key was not present
        Instant previous = activeUsers.putIfAbsent(userId, Instant.now());
        if (previous != null) {
            log.info("User {} already has an active request, rejecting", userId);
            return false;
        }
        return true;
    }

    /**
     * Releases the slot for the user after request completes.
     */
    public void release(String userId) {
        activeUsers.remove(userId);
        log.debug("Released rate limit slot for user {}", userId);
    }

    /**
     * Self-healing: Periodically clear users who have been 'busy' for too long.
     * Prevents users from being stuck if an unexpected error occurs during
     * processing.
     */
    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void cleanupTimedOutRequests() {
        Instant now = Instant.now();
        activeUsers.entrySet().removeIf(entry -> {
            boolean timedOut = Duration.between(entry.getValue(), now).getSeconds() > REQUEST_TIMEOUT_SECONDS;
            if (timedOut) {
                log.warn("Self-healing: Force-released rate limit for user {} after timeout", entry.getKey());
            }
            return timedOut;
        });
    }

    // Needed for removeIf reference above
    private static class Duration {
        static java.time.Duration between(Instant start, Instant end) {
            return java.time.Duration.between(start, end);
        }
    }

    public boolean isActive(String userId) {
        return activeUsers.containsKey(userId);
    }

    public void blockUser(String userId) {
        blockedUsers.add(userId);
        activeUsers.remove(userId);
        log.info("User {} has been blocked", userId);
    }

    public void unblockUser(String userId) {
        blockedUsers.remove(userId);
        log.info("User {} has been unblocked", userId);
    }

    public boolean isBlocked(String userId) {
        return blockedUsers.contains(userId);
    }

    public Set<String> getBlockedUsers() {
        return Set.copyOf(blockedUsers);
    }

    public Set<String> getActiveUsers() {
        return Set.copyOf(activeUsers.keySet());
    }
}
