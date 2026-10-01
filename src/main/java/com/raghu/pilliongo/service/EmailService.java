package com.raghu.pilliongo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // Sender address shown on every email. With Gmail this was filled in
    // automatically from the login, but Brevo's login is an
    // @smtp-brevo.com address, so the sender must be set explicitly.
    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.support-inbox}")
    private String supportInbox;

    // ---- OTP emails --------------------------------------------------------
    // Sent as HTML (branded card with the OTP in big digits) plus a plain-text
    // version for email apps that don't show HTML. Wording is shared through
    // buildOtpHtml()/buildOtpText() so both OTP emails look the same.

    public void sendOtpEmail(String toEmail, String otp) {
        String intro = "Welcome to PillionGo! Use the code below to verify your email "
                + "and finish creating your account.";
        sendOtp(toEmail,
                "Your PillionGo verification code: " + otp,
                "Verify your email",
                "Hi there,",
                intro,
                otp,
                "Didn't sign up for PillionGo? You can safely ignore this email. "
                        + "No account will be created without this code.");
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String otp) {
        String name = (fullName != null && !fullName.isBlank()) ? fullName : "there";
        sendOtp(toEmail,
                "Your PillionGo password reset code: " + otp,
                "Reset your password",
                "Hi " + name + ",",
                "We received a request to reset the password for your PillionGo account. "
                        + "Enter the code below to choose a new password.",
                otp,
                "Didn't ask to reset your password? Ignore this email. "
                        + "Your password will not change and your account is still safe.");
    }

    private void sendOtp(String toEmail, String subject, String heading, String greeting,
                         String intro, String otp, String ignoreNote) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(
                    buildOtpText(greeting, intro, otp, ignoreNote),
                    buildOtpHtml(heading, greeting, intro, otp, ignoreNote));
            mailSender.send(mime);
        } catch (MessagingException e) {
            // MailPreparationException is a MailException, so the global
            // handler still turns this into the friendly 503 for the user.
            throw new MailPreparationException("Could not build OTP email", e);
        }
    }

    private static String buildOtpText(String greeting, String intro, String otp, String ignoreNote) {
        return greeting + "\n\n"
                + intro + "\n\n"
                + "Your code: " + otp + "\n"
                + "It expires in 10 minutes and can only be used once.\n\n"
                + "Never share this code with anyone. PillionGo will never ask you for it "
                + "by phone, chat or email.\n\n"
                + ignoreNote + "\n\n"
                + "---\n"
                + "PillionGo: share rides, split costs, travel together.\n"
                + "Riders find drivers already heading their way, for instant rides or "
                + "pre-planned trips.\n"
                + "Need help? Reply to this email.\n";
    }

    private static String buildOtpHtml(String heading, String greeting, String intro,
                                       String otp, String ignoreNote) {
        // Table layout + inline styles: the only thing every email app
        // (Gmail, Outlook, Apple Mail) renders reliably.
        return """
            <!DOCTYPE html>
            <html><body style="margin:0;padding:0;background:#F8FAFC;font-family:Arial,Helvetica,sans-serif;color:#0F172A;">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F8FAFC;padding:32px 12px;">
              <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #E2E8F0;">
                  <tr><td style="background:#EA580C;padding:22px 28px;">
                    <div style="font-size:22px;font-weight:bold;color:#ffffff;">PillionGo</div>
                    <div style="font-size:12px;color:#FFE4D5;margin-top:2px;">Share rides. Split costs. Travel together.</div>
                  </td></tr>
                  <tr><td style="padding:28px;">
                    <h1 style="margin:0 0 16px;font-size:20px;color:#0F172A;">%s</h1>
                    <p style="margin:0 0 8px;font-size:15px;">%s</p>
                    <p style="margin:0 0 22px;font-size:15px;line-height:1.5;color:#334155;">%s</p>
                    <div style="text-align:center;background:#FFF1E8;border:1px dashed #EA580C;border-radius:12px;padding:18px 10px;">
                      <div style="font-size:12px;letter-spacing:1px;color:#9A3412;text-transform:uppercase;">Your code</div>
                      <div style="font-size:34px;font-weight:bold;letter-spacing:8px;color:#0F172A;margin-top:6px;">%s</div>
                      <div style="font-size:12px;color:#9A3412;margin-top:6px;">Expires in 10 minutes &middot; single use</div>
                    </div>
                    <p style="margin:22px 0 0;font-size:13px;line-height:1.5;color:#B91C1C;">
                      &#128274; Never share this code with anyone. PillionGo will never ask for it by phone, chat or email.
                    </p>
                    <p style="margin:12px 0 0;font-size:13px;line-height:1.5;color:#64748B;">%s</p>
                  </td></tr>
                  <tr><td style="background:#F1F5F9;padding:20px 28px;font-size:12px;line-height:1.6;color:#64748B;">
                    <b style="color:#0F172A;">What is PillionGo?</b><br>
                    A ride-sharing app where riders find drivers already heading their way, for instant
                    rides or pre-planned trips by bike or car, and everyone splits the cost.<br><br>
                    Need help? Just reply to this email.<br>
                    &copy; PillionGo
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """.formatted(escape(heading), escape(greeting), escape(intro), escape(otp), escape(ignoreNote));
    }

    // Names come from user input, so never drop them into HTML unescaped.
    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // Support inbox notification, sent to the developer/admin mailbox every
    // time a user submits an in-app help message — so a new ticket doesn't
    // sit unseen until someone happens to open the admin panel.
    public void sendSupportNotification(String fromUserName, String fromUserEmail, String subject, String messageBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(supportInbox);
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
            message.setFrom(fromAddress);
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