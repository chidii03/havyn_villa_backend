package com.havyn.auth.domain;

/**
 * The two transactional emails auth sends. Scoped to auth on purpose — see ADR-010 for
 * why {@link com.havyn.common.ratelimit.RateLimiter} lives in {@code common/} but this
 * doesn't: nothing outside auth needs it yet. When prompt 16 (messaging/notifications)
 * builds the channel-agnostic notification service, this can be folded into it.
 *
 * <p><strong>Implementations must not throw on delivery failure.</strong> Both methods
 * are called from {@code AuthService} as a side effect of an operation that must
 * succeed on its own merits — {@code register()} creates the account regardless of
 * whether the verification email actually sends, and {@code requestPasswordReset()}'s
 * own "no account enumeration" contract depends on a mail outage never producing a
 * different response than "the email doesn't exist." A caller who needs to know
 * delivery failed should check {@code User#isEmailVerified()} or offer a resend, not
 * rely on this throwing.
 */
public interface Mailer {
    void sendEmailVerification(String toEmail, String rawToken);

    void sendPasswordReset(String toEmail, String rawToken);
}
