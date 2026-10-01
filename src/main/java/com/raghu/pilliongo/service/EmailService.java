package com.raghu.pilliongo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("PillionGo - Email Verification OTP");
        message.setText(
                "Welcome to PillionGo!\n\n" +
                        "Your OTP is: " + otp + "\n\n" +
                        "Valid for 10 minutes.\n\n" +
                        "Team PillionGo"
        );
        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("PillionGo - Password Reset OTP");
        message.setText(
                "Hi " + (fullName != null && !fullName.isBlank() ? fullName : "there") + ",\n\n" +
                        "You requested to reset your PillionGo password.\n\n" +
                        "Your OTP is: " + otp + "\n\n" +
                        "This OTP is valid for 10 minutes.\n\n" +
                        "If you did not request this, please ignore this email.\n" +
                        "Your password will not change.\n\n" +
                        "Team PillionGo"
        );
        mailSender.send(message);
    }

    // Support inbox notification, sent to the developer/admin mailbox every
    // time a user submits an in-app help message — so a new ticket doesn't
    // sit unseen until someone happens to open the admin panel.
    public void sendSupportNotification(String fromUserName, String fromUserEmail, String subject, String messageBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("pilliongo.app@gmail.com");
            message.setSubject("[PillionGo Support] New message: " + subject);
            message.setText(
                    "New in-app support message received.\n\n" +
                            "From: " + fromUserName + " (" + fromUserEmail + ")\n" +
                            "Subject: " + subject + "\n\n" +
                            "Message:\n" + messageBody + "\n\n" +
                            "Reply from the Admin Support panel in PillionGo."
            );
            mailSender.send(message);
        } catch (Exception e) {
            // Notification email is a courtesy, not a requirement — the
            // message itself is already saved and visible in the admin
            // panel, so a mail-server hiccup here shouldn't fail the
            // request or block the user from submitting their message.
        }
    }

    // Sent to the user once the admin replies, so they get the "1-2 hour
    // reply" they were promised even if they don't come back to the app.
    public void sendSupportReplyEmail(String toEmail, String fullName, String subject, String originalMessage, String reply) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("PillionGo Support - Re: " + subject);
            message.setText(
                    "Hi " + (fullName != null && !fullName.isBlank() ? fullName : "there") + ",\n\n" +
                            "Thanks for reaching out to PillionGo Support. Here's our reply:\n\n" +
                            reply + "\n\n" +
                            "---\n" +
                            "Your original message:\n" + originalMessage + "\n\n" +
                            "Team PillionGo"
            );
            mailSender.send(message);
        } catch (Exception e) {
            // Same reasoning as above — don't fail the admin's reply action
            // over an email delivery issue; the reply is already saved and
            // visible to the user in-app.
        }
    }
}