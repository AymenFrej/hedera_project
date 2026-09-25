/**
 * A token's face: a coin whose colours come from its symbol, so each token is recognisable at a
 * glance without any made-up artwork. The optional ring shows a real fraction, e.g. the share of
 * the supply still in the treasury.
 */
export function hueOf(symbol: string | null | undefined): number {
  let h = 0
  for (const c of symbol ?? '?') h = (h * 31 + c.charCodeAt(0)) % 360
  return h
}

export default function TokenCoin({
  symbol,
  size = 56,
  ring,
}: {
  symbol: string | null | undefined
  size?: number
  /** 0..1, drawn as an arc around the coin; omitted when unknown */
  ring?: number | null
}) {
  const hue = hueOf(symbol)
  const label = (symbol ?? '?').slice(0, 5)
  const r = 44
  const circumference = 2 * Math.PI * r
  const id = `coin-${label}-${hue}`
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label={`${label} token`}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={`hsl(${hue} 70% 62%)`} />
          <stop offset="1" stopColor={`hsl(${(hue + 40) % 360} 60% 38%)`} />
        </linearGradient>
      </defs>
      <circle cx="50" cy="50" r="44" fill="none" stroke="#242a37" strokeWidth="6" />
      {ring != null && (
        <circle
          cx="50"
          cy="50"
          r={r}
          fill="none"
          stroke={`hsl(${hue} 80% 70%)`}
          strokeWidth="6"
          strokeLinecap="round"
          strokeDasharray={`${Math.max(0, Math.min(1, ring)) * circumference} ${circumference}`}
          transform="rotate(-90 50 50)"
        />
      )}
      <circle cx="50" cy="50" r="36" fill={`url(#${id})`} />
      <circle cx="50" cy="50" r="30" fill="none" stroke="#ffffff33" strokeWidth="1.5" strokeDasharray="2 3" />
      <text
        x="50"
        y="50"
        textAnchor="middle"
        dominantBaseline="central"
        fontFamily="'DM Mono', monospace"
        fontWeight="700"
        fontSize={label.length > 3 ? 15 : 20}
        fill="#fff"
      >
        {label}
      </text>
    </svg>
  )
}
