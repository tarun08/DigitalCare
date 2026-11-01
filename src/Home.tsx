import { DigitalCare, type SystemPage } from './plugin'
import type { ScreenProps } from './App'
import { Ring } from './Ring'
import { SetupCard } from './SetupCard'
import { text } from './text'
import { fmtClock, fmtLeft, useNow } from './time'

export function Home({ state, act, go }: ScreenProps) {
  const now = useNow()
  const s = state.settings
  const open = (page: SystemPage) => { DigitalCare.openSystemSettings({ page }).catch(() => {}) }

  const t = text(s.language).home
  const breakLine = state.breakEndAt ? t.breakRunning
    : state.nextBreakAt ? t.nextBreak(state.nextBreakType === 'long', fmtLeft(state.nextBreakAt - now))
    : !s.breaksOn ? t.breaksOff
    : t.noMoreBreaks
  const waterLine = state.nextWaterAt ? t.nextWater(fmtClock(state.nextWaterAt))
    : state.glasses >= state.goal ? t.goalReached
    : !s.waterOn ? t.waterOff
    : state.waterCutoffAt && now < state.waterCutoffAt ? t.stopAt(fmtClock(state.waterCutoffAt))
    : t.noMoreWater
  // Seconds until the first minute is done, then whole minutes.
  const inUse = (ms: number) => ms < 60_000 ? t.seconds(Math.max(0, Math.floor(ms / 1000))) : t.minutes(Math.floor(ms / 60_000))
  const phoneLine = !s.screenOn ? null
    : !state.phoneRunning ? t.counterDown
    : state.phoneInUseSince ? t.inUse(inUse(now - state.phoneInUseSince))
    : t.notInUse
  const reminded = state.phoneRemindedAt && new Date(state.phoneRemindedAt).toDateString() === new Date(now).toDateString()
    ? state.phoneRemindedAt : null
  const runs = (a: number | null, b: number | null) => a && b ? t.runs(fmtClock(a), fmtClock(b)) : null
  // A full-screen reminder that did not show today, and why: the user sees the cause without asking.
  const tf = text(s.language).full
  const fullMissed = state.fullScreenAt && state.fullScreenResult !== 'shown'
    && new Date(state.fullScreenAt).toDateString() === new Date(now).toDateString() ? state.fullScreenAt : null
  const breakHours = s.breaksOn ? runs(state.dayStartAt, state.dayEndAt) : null
  const waterHours = s.waterOn ? runs(state.waterStartAt, state.waterCutoffAt) : null
  const phoneHours = runs(state.dayStartAt, state.dayEndAt)

  return (
    <>
      <header className="top">
        <h1>{t.title}</h1>
        <button className="icon" aria-label={t.settings} onClick={() => go('settings')}>
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
              {state.pausedUntil === state.nextDayStartAt ? t.pausedTomorrow : t.pausedUntil(fmtClock(state.pausedUntil))}
            </p>
            <button className="primary" onClick={() => act(DigitalCare.resume())}>{t.resume}</button>
          </>
        ) : !state.inDay ? (
          <p className="big">{state.nextDayStartAt && t.startsAt(fmtClock(state.nextDayStartAt))}</p>
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
        {phoneLine && reminded && <p className="hours">{t.lastReminder(fmtClock(reminded))}</p>}
        {fullMissed && <p className="hours">{tf.line(fmtClock(fullMissed), tf.why(state.fullScreenResult))}</p>}
      </section>

      <section className="card center">
        <Ring fraction={state.glasses / state.goal}>
          <strong>{state.glasses} / {state.goal}</strong>
          <span>{t.glasses}</span>
          <span className="muted">{t.litres((state.glasses * s.glassMl / 1000).toFixed(1))}</span>
        </Ring>
        <div className="row">
          <button disabled={state.glasses === 0} onClick={() => act(DigitalCare.removeGlass())}>{t.minusGlass}</button>
          <button className="primary" onClick={() => act(DigitalCare.logGlass())}>{t.plusGlass}</button>
        </div>
        <p>{state.tip}</p>
        <p className="muted">{t.beforeMeals}</p>
      </section>

      {state.inDay && !state.pausedUntil && (
        <section className="card">
          <h2>{t.pause}</h2>
          <div className="row">
            <button onClick={() => act(DigitalCare.pause({ minutes: 30 }))}>{t.min30}</button>
            <button onClick={() => act(DigitalCare.pause({ minutes: 60 }))}>{t.hour1}</button>
            <button onClick={() => act(DigitalCare.pause({ minutes: null }))}>{t.untilTomorrow}</button>
          </div>
        </section>
      )}

      <section className="card">
        <h2>{t.comfort}</h2>
        <button onClick={() => open('nightLight')}>{t.nightLight}</button>
        <p className="muted">{t.nightHint}</p>
        <button onClick={() => open('extraDim')}>{t.extraDim}</button>
        <p className="muted">{t.extraHint}</p>
      </section>
    </>
  )
}
