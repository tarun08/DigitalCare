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
    : state.waterCutoffAt && now < state.waterCutoffAt ? `Reminders stop at ${fmtClock(state.waterCutoffAt)}`
    : 'No more water reminders today'
  // Seconds until the first minute is done, then whole minutes.
  const inUse = (ms: number) => ms < 60_000 ? `${Math.max(0, Math.floor(ms / 1000))} s` : `${Math.floor(ms / 60_000)} min`
  const phoneLine = !s.screenOn ? null
    : !state.phoneRunning ? 'Phone-use counter is not running'
    : state.phoneInUseSince ? `Phone in use: ${inUse(now - state.phoneInUseSince)} in a row`
    : 'Phone not in use'
  const reminded = state.phoneRemindedAt && new Date(state.phoneRemindedAt).toDateString() === new Date(now).toDateString()
    ? state.phoneRemindedAt : null
  const runs = (a: number | null, b: number | null) => a && b ? `Runs ${fmtClock(a)} to ${fmtClock(b)}` : null
  const breakHours = s.breaksOn ? runs(state.dayStartAt, state.dayEndAt) : null
  const waterHours = s.waterOn ? runs(state.waterStartAt, state.waterCutoffAt) : null
  const phoneHours = runs(state.dayStartAt, state.dayEndAt)

  return (
    <>
      <header className="top">
        <h1>Digital Care</h1>
        <button className="icon" aria-label="Settings" onClick={() => go('settings')}>
          <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.488.488 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
          </svg>
        </button>
      </header>
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
            {breakHours && <p className="hours">{breakHours}</p>}
            <p>{waterLine}</p>
            {waterHours && <p className="hours">{waterHours}</p>}
          </>
        )}
        {phoneLine && <p className="muted">{phoneLine}</p>}
        {phoneLine && phoneHours && <p className="hours">{phoneHours}</p>}
        {phoneLine && reminded && <p className="hours">Last reminder at {fmtClock(reminded)}</p>}
      </section>

      <section className="card center">
        <Ring fraction={state.glasses / state.goal}>
          <strong>{state.glasses} / {state.goal}</strong>
          <span>glasses</span>
          <span className="muted">{(state.glasses * s.glassMl / 1000).toFixed(1)} L</span>
        </Ring>
        <div className="row">
          <button disabled={state.glasses === 0} onClick={() => act(DigitalCare.removeGlass())}>&minus;1 glass</button>
          <button className="primary" onClick={() => act(DigitalCare.logGlass())}>+1 glass</button>
        </div>
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
        <p className="muted">Your phone may call it “Eye protection”.</p>
        <button onClick={() => open('extraDim')}>Extra dim</button>
        <p className="muted">Look for “Extra dim”. Your phone may not have it.</p>
      </section>
    </>
  )
}
