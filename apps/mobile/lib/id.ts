/** A locally-unique-enough string for an Idempotency-Key header — doesn't need to be a real UUID, just unique per submit attempt. */
export function generateIdempotencyKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
