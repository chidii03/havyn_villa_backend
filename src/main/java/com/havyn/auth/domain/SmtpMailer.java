package com.havyn.auth.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
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
                brandedHtml(
                        "Verify your email",
                        "Welcome to Havyn Villa. Confirm your email address before booking or hosting.",
                        "Verify email",
                        link,
                        "This link expires in 24 hours. If you didn't create an account, ignore this email."));
    }

    @Override
    public void sendPasswordReset(String toEmail, String rawToken) {
        String link = webBaseUrl + "/reset-password?token=" + encode(rawToken);
        send(toEmail, "Reset your Havyn Villa password",
                brandedHtml(
                        "Reset your password",
                        "We received a request to reset your Havyn Villa password.",
                        "Reset password",
                        link,
                        "This link expires in 1 hour. If you didn't request this, ignore this email."));
    }

    /**
     * Never throws — see {@link Mailer}'s class doc for why. A down/misconfigured mail
     * server (Mailhog not running locally, a real provider outage in prod) degrades to
     * "the email didn't go out," not "the account couldn't be created" or "password
     * reset silently 500s only when the address is real."
     */
    private void send(String to, String subject, String body) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email sent subject=\"{}\" recipientHash={}", subject, shortHash(to));
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.warn(
                    "Failed to send email subject=\"{}\" recipientHash={} error={}",
                    subject,
                    shortHash(to),
                    ex.getMessage(),
                    ex);
        }
    }

    private String brandedHtml(String title, String intro, String action, String link, String footnote) {
        return """
                <!doctype html>
                <html>
                  <body style="margin:0;background:#f4f8ff;font-family:Arial,sans-serif;color:#172033">
                    <div style="max-width:560px;margin:0 auto;padding:24px">
                      <div style="background:#003da6;color:#fff;padding:22px;border-radius:8px 8px 0 0">
                        <h1 style="margin:0;font-size:26px">Havyn Villa</h1>
                        <p style="margin:6px 0 0">Stay beautiful, live better.</p>
                      </div>
                      <div style="background:#fff;border:1px solid #dfe8f5;border-top:0;padding:24px;border-radius:0 0 8px 8px">
                        <h2 style="margin:0 0 12px">%s</h2>
                        <p>%s</p>
                        <p style="margin:24px 0">
                          <a href="%s" style="background:#003da6;color:#fff;text-decoration:none;padding:12px 18px;border-radius:8px;display:inline-block">%s</a>
                        </p>
                        <p style="word-break:break-all;color:#5d6b82">%s</p>
                        <p style="border-top:1px solid #dfe8f5;margin-top:24px;padding-top:16px;color:#5d6b82">%s</p>
                      </div>
                    </div>
                  </body>
                </html>
                """
                .formatted(escape(title), escape(intro), escape(link), escape(action), escape(link), escape(footnote));
    }

    private String escape(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
