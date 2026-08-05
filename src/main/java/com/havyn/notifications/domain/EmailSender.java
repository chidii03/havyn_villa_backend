package com.havyn.notifications.domain;

/**
 * Provider-abstracted email channel for notifications — see
 * project-docs/prompts/16-messaging-notifications.md's "Emails via provider
 * abstraction" constraint. Deliberately separate from {@code auth.domain.Mailer}
 * (that port is narrowly scoped to auth's two specific flows — verify/reset — not a
 * general-purpose send); this one is generic so any notification type can use it.
 */
public interface EmailSender {

    void send(String toEmail, String subject, String body);
}
