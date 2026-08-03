-- V11__bookings_enabled_setting.sql
-- Launch-checklist follow-up (project-docs/roadmap/02-launch-checklist.md #7): the
-- only runtime lever to stop new bookings today was a full redeploy/scale-to-zero.
-- Seeds a real application-level kill switch into the existing platform_setting
-- table (V5__booking.sql) — no schema change, same key/value shape commission_pct
-- already uses. Editable immediately via the existing PUT /admin/settings/{key}
-- (admin.service.AdminSettingsService, session 19) with no frontend change needed.

INSERT INTO platform_setting (key, value) VALUES ('bookings_enabled', 'true');
