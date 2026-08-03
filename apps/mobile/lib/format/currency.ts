/**
 * Mirrors apps/web/src/lib/format/currency.ts exactly — Hermes (Expo SDK 50+) ships
 * full ICU data by default, so Intl.NumberFormat behaves identically on-device.
 */
export function formatPrice(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-NG", {
    style: "currency",
    currency,
    maximumFractionDigits: Number.isInteger(amount) ? 0 : 2,
  }).format(amount);
}
