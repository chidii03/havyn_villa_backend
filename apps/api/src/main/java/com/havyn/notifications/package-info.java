/**
 * Channel-agnostic notification service (in-app + email now; realtime later via
 * WebSocket/SSE/Redis pub-sub — see architecture/01-system-architecture.md's session 17
 * notes for the documented-but-not-built realtime design). Implemented in prompt 16
 * (session 17) — see project-docs/prompts/16-messaging-notifications.md.
 */
package com.havyn.notifications;
