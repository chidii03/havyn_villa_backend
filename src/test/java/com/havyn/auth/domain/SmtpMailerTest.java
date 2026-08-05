package com.havyn.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The exact bug this covers: a down/unreachable mail server (Mailhog not running
 * locally — no Docker in this dev sandbox, same root cause documented elsewhere in this
 * project) must never surface as a failure of the operation that triggered the email —
 * see {@link Mailer}'s class doc. Before this fix, {@code register()} rolled back the
 * whole account-creation transaction whenever Mailhog was down, and {@code
 * requestPasswordReset()} broke its own "no account enumeration" contract by 500ing
 * only when the target address was real.
 */
class SmtpMailerTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpMailer mailer = new SmtpMailer(mailSender, "http://localhost:3000", "no-reply@havynvilla.com");

    @Test
    void sendEmailVerification_doesNotThrow_whenTheMailServerIsUnreachable() {
        doThrow(new MailSendException("Couldn't connect to host, port: localhost, 1025"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailer.sendEmailVerification("guest@example.com", "raw-token"))
                .doesNotThrowAnyException();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendPasswordReset_doesNotThrow_whenTheMailServerIsUnreachable() {
        doThrow(new MailSendException("Couldn't connect to host, port: localhost, 1025"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailer.sendPasswordReset("guest@example.com", "raw-token"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendEmailVerification_stillSendsNormally_whenTheMailServerWorks() {
        mailer.sendEmailVerification("guest@example.com", "raw-token");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
