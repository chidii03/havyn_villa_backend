package com.havyn.notifications.domain;

import org.springframework.beans.factory.annotation.Value;
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
        mailSender.send(message);
    }
}
