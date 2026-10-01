package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMyNotifications(Authentication auth) {
        return ResponseEntity.ok(notificationService.getMyNotifications(auth.getName()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication auth) {
        return ResponseEntity.ok(Map.of("unread", notificationService.getUnreadCount(auth.getName())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(notificationService.markRead(id, auth.getName()));
    }

    @PutMapping("/read-all")
    public ResponseEntity<String> markAllRead(Authentication auth) {
        return ResponseEntity.ok(notificationService.markAllRead(auth.getName()));
    }
}
