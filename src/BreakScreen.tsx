import { DigitalCare } from './plugin'
import type { ScreenProps } from './App'
import { Anim } from './Anim'
import { Ring } from './Ring'
import { fmtLeft, useNow } from './time'

export function BreakScreen({ state, act, go, type }: ScreenProps & { type: 'short' | 'long' }) {
  const now = useNow()
  const long = type === 'long'
  const total = long ? state.settings.longMin * 60_000 : state.settings.shortSec * 1000
  const left = state.breakEndAt ? state.breakEndAt - now : 0

  return (
    <section className="card center">
      <h1>{long ? 'Long break' : 'Eye break'}</h1>
      {long ? <Anim name="stretch" alt="Person stretching" /> : <Anim name="eye" alt="Eyes looking into the distance" />}
      <Ring fraction={left / total}>
        <strong>{left > 0 ? `${fmtLeft(left)} left` : 'Break over'}</strong>
      </Ring>
      <p>{long ? 'Stand up, walk around, stretch your back and neck.' : 'Look at something about 6 m (20 ft) away. Blink slowly.'}</p>
      <div className="row">
        <button className="primary" onClick={() => go('home')}>Done</button>
        {left > 0 && <button onClick={() => { act(DigitalCare.skipBreak()); go('home') }}>Skip</button>}
      </div>
    </section>
  )
}
