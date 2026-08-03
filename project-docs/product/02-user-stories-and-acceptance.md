# User Stories & Acceptance Criteria (MVP)

> Status: Draft · Phase 1. Format: As a <role>, I want <goal>, so that <value>. AC in Given/When/Then.

## Auth
**US-A1** As a visitor I want to register so that I can book.
- Given valid email+password, When I submit, Then an account is created and a verification email is sent.
- Given an existing email, When I register, Then I get a clear "email in use" error (no account enumeration leak in timing).

**US-A2** As a user I want to log in and stay logged in securely.
- Given valid credentials, When I log in, Then I receive an access token (short-lived) and a rotating refresh token in a secure, httpOnly cookie.

## Discovery
**US-D1** As a guest I want to search by destination/dates/guests so that I see relevant stays.
- Given a destination and valid date range, When I search, Then only available properties matching capacity are returned, paginated, with map markers.

**US-D2** As a guest I want filters so that I can narrow results.
- Given active filters, When applied, Then results and the map update and filter state is reflected in the URL (shareable).

## Booking
**US-B1** As a traveler I want a correct, itemized total before I commit.
- Given selected valid dates/guests, When the widget loads, Then the **server-computed** breakdown (nights, base, cleaning, service, discounts, taxes, total) is shown; the frontend never computes the authoritative total.

**US-B2** As a traveler I want to never be double-charged or double-booked.
- Given two concurrent bookings for overlapping dates, When both attempt to confirm, Then exactly one succeeds; the other gets a clear "no longer available" error. Enforced by a transactional check + temporary hold + DB constraint.

**US-B3** As a traveler I want to cancel per policy and understand my refund.
- Given a booking within the free-cancellation window, When I cancel, Then the refund state and amount reflect the policy and a refund is initiated via the payment provider.

## Payments
**US-P1** As a traveler I want to pay securely.
- Given a confirmed price, When I pay, Then the backend creates the intent, the provider handles card data, and the booking is only marked paid after backend webhook verification.

## Host
**US-H1** As a host I want to publish a listing.
- Given required fields + at least N photos, When I publish, Then the listing enters moderation (or goes live per policy) and appears in search when active.

**US-H2** As a host I want calendar/price control and to avoid double-bookings.
- Given blocked dates, When a guest searches those dates, Then the property is excluded.

## Reviews
**US-R1** As a traveler I want to review only stays I completed.
- Given a completed, eligible booking, When I submit a review, Then it publishes and updates the property's aggregate rating; ineligible users cannot review.

## Admin
**US-M1** As an admin I want to moderate listings and resolve disputes.
- Given a reported listing, When I action it, Then its status changes and an audit log entry is recorded.
