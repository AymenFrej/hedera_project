/**
 * Envelope balances arrive in tinybars, the unit the policy engine decides in and the unit Payments
 * sends (1 ℏ = 100,000,000 tinybars). Printing them raw puts "50000000000" on screen where a human
 * expects "500 ℏ", so every envelope shown to a person goes through here.
 *
 * The backend already formats its own explanation amounts this way; this is the same conversion on
 * the screens that read the policy state directly.
 */
const TINYBARS_PER_HBAR = 100_000_000

/** Tinybars as a human-readable ℏ amount, without trailing zeroes: 50000000000 becomes "500". */
export function hbar(tinybars: number | string): string {
  const value = Number(tinybars)
  if (!Number.isFinite(value)) return String(tinybars)
  const asHbar = value / TINYBARS_PER_HBAR
  // Up to 8 decimals, but no trailing zeroes: 500 stays "500", 0.5 stays "0.5".
  return asHbar.toFixed(8).replace(/\.?0+$/, '')
}

/** What a person typed in ℏ, as the tinybars the engine decides in. */
export function toTinybars(amountInHbar: number | string): number {
  return Math.round(Number(amountInHbar) * TINYBARS_PER_HBAR)
}
