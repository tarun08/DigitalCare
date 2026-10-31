import { useState } from 'react'
import { EyeCare, type Settings } from './plugin'
import type { ScreenProps } from './App'

type KeyOf<T> = { [K in keyof Settings]: Settings[K] extends T ? K : never }[keyof Settings]

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const CHANNELS: [string, string][] = [
  ['short_break', 'Short eye break'], ['long_break', 'Long break'], ['break_over', 'Break over'], ['water', 'Water'],
]

export function SettingsScreen({ state, act, go }: ScreenProps) {
  const [draft, setDraft] = useState<Settings>(state.settings)
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setDraft(d => ({ ...d, [k]: v }))

  // An emptied field sends NaN -> null, and the engine keeps that setting's default.
  const num = (label: string, k: KeyOf<number>, min: number, max: number) => (
    <label className="field">
      {label}
      <input type="number" inputMode="numeric" min={min} max={max}
        value={Number.isNaN(draft[k]) ? '' : draft[k]} onChange={e => set(k, e.target.valueAsNumber)} />
    </label>
  )
  const time = (label: string, k: KeyOf<string>) => (
    <label className="field">
      {label}
      <input type="time" value={draft[k]} onChange={e => set(k, e.target.value)} />
    </label>
  )
  const check = (label: string, k: KeyOf<boolean>) => (
    <label className="check">
      <input type="checkbox" checked={draft[k]} onChange={e => set(k, e.target.checked)} />
      {label}
    </label>
  )
  const toggleDay = (day: number, on: boolean) =>
    set('weekendDays', on ? [...draft.weekendDays, day].sort() : draft.weekendDays.filter(d => d !== day))

  return (
    <>
      <h1>Settings</h1>

      <section className="card">
        <h2>Day</h2>
        <div className="grid">
          {time('Weekday wake', 'weekdayWake')}
          {time('Weekday sleep', 'weekdaySleep')}
          {time('Weekend wake', 'weekendWake')}
          {time('Weekend sleep', 'weekendSleep')}
        </div>
        <fieldset>
          <legend>Weekend days</legend>
          <div className="days">
            {DAYS.map((name, i) => (
              <label key={name} className="check">
                <input type="checkbox" checked={draft.weekendDays.includes(i + 1)}
                  onChange={e => toggleDay(i + 1, e.target.checked)} />
                {name}
              </label>
            ))}
          </div>
        </fieldset>
      </section>

      <section className="card">
        <h2>Eye breaks</h2>
        {check('Eye breaks on', 'breaksOn')}
        <div className="grid">
          {num('Short break every (min)', 'shortEveryMin', 1, 60)}
          {num('Short break length (s)', 'shortSec', 10, 60)}
          <label className="field">
            Long break every
            <select value={draft.longEveryN} onChange={e => set('longEveryN', Number(e.target.value))}>
              {[2, 3, 4, 5, 6].map(n => <option key={n} value={n}>{n * (draft.shortEveryMin || 20)} min</option>)}
            </select>
          </label>
          {num('Long break length (min)', 'longMin', 1, 15)}
        </div>
      </section>

      <section className="card">
        <h2>Water</h2>
        {check('Water reminders on', 'waterOn')}
        {check('Smart water (season goal)', 'smartWater')}
        <div className="grid">
          {!draft.smartWater && num('Custom goal (glasses)', 'customGoal', 1, 20)}
          {!draft.smartWater && num('Every (min)', 'customEveryMin', 1, 180)}
          {num('Glass size (ml)', 'glassMl', 100, 500)}
          {num('Stop before sleep (min)', 'stopBeforeSleepMin', 0, 180)}
        </div>
      </section>

      <section className="card">
        <h2>Lock screen</h2>
        {check('Wake screen for reminders', 'wakeScreen')}
        <p className="muted">Shows the break screen on the lock screen.</p>
      </section>

      <section className="card">
        <h2>Sound &amp; vibration</h2>
        <div className="row">
          {CHANNELS.map(([id, label]) => (
            <button key={id} onClick={() => { EyeCare.openSystemSettings({ page: 'channel', channel: id }).catch(() => {}) }}>
              {label}
            </button>
          ))}
        </div>
      </section>

      <p className="muted">General guidance only. If you have a kidney or heart condition, follow your doctor’s advice on water.</p>

      <div className="row">
        <button className="primary" onClick={() => { act(EyeCare.saveSettings(draft)); go('home') }}>Save</button>
        <button onClick={() => go('home')}>Cancel</button>
      </div>
    </>
  )
}
