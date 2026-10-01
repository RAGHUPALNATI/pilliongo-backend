package com.raghu.pilliongo.service;

import com.raghu.pilliongo.model.SupportMessage;
import com.raghu.pilliongo.model.User;
import com.raghu.pilliongo.repository.SupportMessageRepository;
import com.raghu.pilliongo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    private User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Users can also just email pilliongo.app@gmail.com directly — this is
    // the in-app alternative that also lands in the admin's Support panel
    // (and gets tracked/repliable, unlike a plain email).
    public SupportMessage submitMessage(String email, String subject, String messageBody) {
        User user = getCurrentUser(email);

        if (subject == null || subject.isBlank()) {
            throw new RuntimeException("Subject is required");
        }
        if (messageBody == null || messageBody.isBlank()) {
            throw new RuntimeException("Message is required");
        }

        SupportMessage saved = supportMessageRepository.save(
                SupportMessage.builder()
                        .user(user)
                        .subject(subject.trim())
                        .message(messageBody.trim())
                        .status(SupportMessage.SupportStatus.OPEN)
                        .build()
        );

        emailService.sendSupportNotification(user.getFullName(), user.getEmail(), saved.getSubject(), saved.getMessage());
        return saved;
    }

    public List<Map<String, Object>> getMyMessages(String email) {
        User user = getCurrentUser(email);
        return supportMessageRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toMap)
                .toList();
    }

    // ADMIN
    public List<Map<String, Object>> getAllMessages() {
        return supportMessageRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> replyToMessage(Long id, String replyText) {
        if (replyText == null || replyText.isBlank()) {
            throw new RuntimeException("Reply text is required");
        }

        SupportMessage msg = supportMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Support message not found"));

        msg.setAdminReply(replyText.trim());
        msg.setStatus(SupportMessage.SupportStatus.REPLIED);
        msg.setRepliedAt(LocalDateTime.now());
        SupportMessage saved = supportMessageRepository.save(msg);

        emailService.sendSupportReplyEmail(
                msg.getUser().getEmail(),
                msg.getUser().getFullName(),
                msg.getSubject(),
                msg.getMessage(),
                msg.getAdminReply()
        );

        notificationService.notify(
                msg.getUser(),
                "SUPPORT_REPLY",
                "Admin replied to your support message \"" + msg.getSubject() + "\"",
                null
        );

        return toMap(saved);
    }

    public String deleteMessage(Long id) {
        if (!supportMessageRepository.existsById(id)) {
            throw new RuntimeException("Support message not found");
        }
        supportMessageRepository.deleteById(id);
        return "Support message #" + id + " deleted";
    }

    private Map<String, Object> toMap(SupportMessage msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("userName", msg.getUser() != null ? msg.getUser().getFullName() : null);
        map.put("userEmail", msg.getUser() != null ? msg.getUser().getEmail() : null);
        map.put("userRole", msg.getUser() != null ? msg.getUser().getRole() : null);
        map.put("subject", msg.getSubject());
        map.put("message", msg.getMessage());
        map.put("status", msg.getStatus());
        map.put("adminReply", msg.getAdminReply());
        map.put("createdAt", msg.getCreatedAt());
        map.put("repliedAt", msg.getRepliedAt());
        return map;
    }
}
