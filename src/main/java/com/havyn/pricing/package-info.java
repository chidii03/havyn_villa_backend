/**
 * Authoritative pricing: nights x base (+ per-date overrides) + cleaning + service fee
 * - discounts + taxes + commission. Deterministic, currency-aware, exposed via
 * POST /properties/{id}/quote and reused by booking confirm. Implemented in prompt 12.
 */
package com.havyn.pricing;
