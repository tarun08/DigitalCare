import { DigitalCare, type SystemPage } from './plugin'
import type { ScreenProps } from './App'
import { Ring } from './Ring'
import { SetupCard } from './SetupCard'
import { fmtClock, fmtLeft, useNow } from './time'

export function Home({ state, act, go }: ScreenProps) {
  const now = useNow()
  const s = state.settings
  const open = (page: SystemPage) => { DigitalCare.openSystemSettings({ page }).catch(() => {}) }

  const breakLine = state.breakEndAt ? 'Break in progress'
    : state.nextBreakAt ? `Next ${state.nextBreakType === 'long' ? 'long break' : 'eye break'} in ${fmtLeft(state.nextBreakAt - now)}`
    : !s.breaksOn ? 'Eye breaks off'
    : 'No more eye breaks today'
  const waterLine = state.nextWaterAt ? `Next water at ${fmtClock(state.nextWaterAt)}`
    : state.glasses >= state.goal ? 'Water goal reached'
    : !s.waterOn ? 'Water reminders off'
    : 'No more water reminders today'

  return (
    <>
      <h1>DigitalCare</h1>
      <SetupCard state={state} />

      <section className="card">
        {state.pausedUntil ? (
          <>
            <p className="big">
              {state.pausedUntil === state.nextDayStartAt ? 'Paused until tomorrow' : `Paused until ${fmtClock(state.pausedUntil)}`}
            </p>
            <button className="primary" onClick={() => act(DigitalCare.resume())}>Resume</button>
          </>
        ) : !state.inDay ? (
          <p className="big">Reminders start at {state.nextDayStartAt && fmtClock(state.nextDayStartAt)}</p>
        ) : (
          <>
            <p className="big">{breakLine}</p>
            <p>{waterLine}</p>
          </>
        )}
      </section>

      <section className="card center">
        <Ring fraction={state.glasses / state.goal}>
          <strong>{state.glasses} / {state.goal}</strong>
          <span>glasses</span>
          <span className="muted">{(state.glasses * s.glassMl / 1000).toFixed(1)} L</span>
        </Ring>
        <button className="primary" onClick={() => act(DigitalCare.logGlass())}>+1 glass</button>
        <p>{state.tip}</p>
        <p className="muted">A glass about 30 min before meals.</p>
      </section>

      {state.inDay && !state.pausedUntil && (
        <section className="card">
          <h2>Pause reminders</h2>
          <div className="row">
            <button onClick={() => act(DigitalCare.pause({ minutes: 30 }))}>30 min</button>
            <button onClick={() => act(DigitalCare.pause({ minutes: 60 }))}>1 h</button>
            <button onClick={() => act(DigitalCare.pause({ minutes: null }))}>Until tomorrow</button>
          </div>
        </section>
      )}

      <section className="card">
        <h2>Screen comfort</h2>
        <button onClick={() => open('nightLight')}>Night light</button>
        <p className="muted">On Vivo this is called “Eye protection”.</p>
        <button onClick={() => open('extraDim')}>Extra dim</button>
        <p className="muted">Look for “Extra dim”. It may not exist on Vivo.</p>
      </section>

      <button onClick={() => go('settings')}>Settings</button>
    </>
  )
}
