package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.Notification;
import com.raghu.pilliongo.model.Role;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.NotificationRepository;
import com.raghu.pilliongo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Internal helper other services call on real events — never on a poll.
    public void notify(User recipient, String type, String message, Long relatedRideId) {
        if (recipient == null) return;
        notificationRepository.save(
                Notification.builder()
                        .recipient(recipient)
                        .type(type)
                        .message(message)
                        .relatedRideId(relatedRideId)
                        .read(false)
                        .build()
        );
    }

    // Fans out to every admin — used for SOS alerts, where the whole point
    // is that it reaches whoever's managing the platform. The admin list is
    // tiny (typically one account), so this never becomes a real fan-out
    // cost.
    public void notifyAllAdmins(String type, String message, Long relatedRideId) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .forEach(admin -> notify(admin, type, message, relatedRideId));
    }

    public List<Map<String, Object>> getMyNotifications(String email) {
        User user = getCurrentUser(email);
        return notificationRepository.findAllByRecipientOrderByCreatedAtDesc(user).stream()
                .map(this::toMap)
                .toList();
    }

    public long getUnreadCount(String email) {
        User user = getCurrentUser(email);
        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    public Map<String, Object> markRead(Long id, String email) {
        User user = getCurrentUser(email);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!n.getRecipient().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied.");
        }
        n.setRead(true);
        return toMap(notificationRepository.save(n));
    }

    public String markAllRead(String email) {
        User user = getCurrentUser(email);
        List<Notification> mine = notificationRepository.findAllByRecipientOrderByCreatedAtDesc(user);
        mine.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(mine);
        return "All notifications marked as read.";
    }

    private Map<String, Object> toMap(Notification n) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", n.getId());
        map.put("type", n.getType());
        map.put("message", n.getMessage());
        map.put("relatedRideId", n.getRelatedRideId());
        map.put("read", n.isRead());
        map.put("createdAt", n.getCreatedAt());
        return map;
    }
}
