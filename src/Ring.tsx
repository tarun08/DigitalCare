import type { ReactNode } from 'react'

const R = 54
const C = 2 * Math.PI * R

/** Progress ring; the children carry the same value as visible text. */
export function Ring({ fraction, children }: { fraction: number; children: ReactNode }) {
  const f = Math.min(1, Math.max(0, fraction || 0))
  return (
    <div className="ring">
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle cx="60" cy="60" r={R} className="ring-track" />
        <circle cx="60" cy="60" r={R} className="ring-fill" strokeDasharray={C} strokeDashoffset={C * (1 - f)}
          transform="rotate(-90 60 60)" />
      </svg>
      <div className="ring-text">{children}</div>
    </div>
  )
}
