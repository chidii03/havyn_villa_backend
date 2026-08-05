package com.havyn.auth.domain;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Local dev / MVP {@link Mailer}: plain SMTP via Spring's {@link JavaMailSender},
 * pointed at Mailhog locally (see infra/docker-compose.yml, spring.mail.* in
 * application.yml). A production HTTP-API provider (Resend/SES/Postmark — see
 * MAIL_PROVIDER/MAIL_API_KEY in .env.example and
 * project-docs/architecture/04-integrations.md#4) is a follow-up adapter behind this
 * same interface, not a change to callers.
 */
@Component
public class SmtpMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    private final JavaMailSender mailSender;
    private final String webBaseUrl;
    private final String mailFrom;

    public SmtpMailer(
            JavaMailSender mailSender,
            @Value("${havyn.web-base-url}") String webBaseUrl,
            @Value("${havyn.mail-from}") String mailFrom) {
        this.mailSender = mailSender;
        this.webBaseUrl = webBaseUrl;
        this.mailFrom = mailFrom;
    }

    @Override
    public void sendEmailVerification(String toEmail, String rawToken) {
        String link = webBaseUrl + "/verify-email?token=" + encode(rawToken);
        send(toEmail, "Verify your Havyn Villa email",
                "Welcome to Havyn Villa. Confirm your email address:\n\n" + link
                        + "\n\nThis link expires in 24 hours.");
    }

    @Override
    public void sendPasswordReset(String toEmail, String rawToken) {
        String link = webBaseUrl + "/reset-password?token=" + encode(rawToken);
        send(toEmail, "Reset your Havyn Villa password",
                "We received a request to reset your password:\n\n" + link
                        + "\n\nThis link expires in 1 hour. If you didn't request this, ignore this email.");
    }

    /**
     * Never throws — see {@link Mailer}'s class doc for why. A down/misconfigured mail
     * server (Mailhog not running locally, a real provider outage in prod) degrades to
     * "the email didn't go out," not "the account couldn't be created" or "password
     * reset silently 500s only when the address is real."
     */
    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send email (subject=\"{}\"), continuing without it: {}", subject, ex.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
