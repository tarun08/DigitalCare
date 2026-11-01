import { useState } from 'react'
import { DigitalCare } from './plugin'
import type { ScreenProps } from './App'
import { Anim } from './Anim'
import { Ring } from './Ring'
import { text } from './text'
import { fmtLeft, useNow } from './time'

export function BreakScreen({ state, act, done, type }: ScreenProps & { type: 'short' | 'long' | 'phone' }) {
  const now = useNow()
  const t = text(state.settings.language).reminder
  const [opened] = useState(now) // a phone-use break has no scheduled end: it counts down from when the screen opens
  const long = type === 'long'
  const phone = type === 'phone'
  const total = long ? state.settings.longMin * 60_000 : state.settings.shortSec * 1000
  const left = phone ? opened + total - now : state.breakEndAt ? state.breakEndAt - now : 0
  const minutes = state.phoneInUseSince ? Math.floor((now - state.phoneInUseSince) / 60_000) : 0

  return (
    <section className="card center">
      <h1>{phone ? t.phoneBreak : long ? t.longBreak : t.eyeBreak}</h1>
      {long ? <Anim name="stretch" alt={t.stretchAlt} /> : <Anim name="eye" alt={t.eyeAlt} />}
      <Ring fraction={left / total}>
        <strong>{left > 0 ? t.left(fmtLeft(left)) : t.over}</strong>
      </Ring>
      <p>{long ? t.longText : phone ? t.phoneText(minutes) : t.shortText}</p>
      <div className="row">
        <button className="primary" onClick={done}>{t.done}</button>
        {left > 0 && !phone && <button onClick={() => { act(DigitalCare.skipBreak()); done() }}>{t.skip}</button>}
      </div>
    </section>
  )
}
