package com.havyn.notifications.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Local dev / MVP {@link EmailSender}: plain SMTP via the same auto-configured
 * {@link JavaMailSender} bean {@code auth.domain.SmtpMailer} uses (Mailhog locally —
 * see infra/docker-compose.yml, spring.mail.* in application.yml). A production
 * HTTP-API provider is a follow-up adapter behind this same interface, not a change to
 * callers — same pattern as {@code SmtpMailer}'s own Javadoc documents.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${havyn.mail-from}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Email sent subject=\"{}\" recipientHash={}", subject, shortHash(toEmail));
        } catch (MailException e) {
            log.warn("Failed to send email subject=\"{}\" recipientHash={} error={}", subject, shortHash(toEmail), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void sendHtml(String toEmail, String subject, String htmlBody) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("HTML email sent subject=\"{}\" recipientHash={}", subject, shortHash(toEmail));
        } catch (MailException e) {
            log.warn("Failed to send HTML email subject=\"{}\" recipientHash={} error={}", subject, shortHash(toEmail), e.getMessage(), e);
            throw e;
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Unable to build email message", e);
        }
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.toLowerCase().getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
