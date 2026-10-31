import { useState, type ReactNode } from 'react'
import { DigitalCare, type AlertId, type Settings } from './plugin'
import type { ScreenProps } from './App'
import { fmtClock } from './time'

type KeyOf<T> = { [K in keyof Settings]: Settings[K] extends T ? K : never }[keyof Settings]

const WEEK = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
const SOUNDS = ['Sound + vibration', 'Sound only', 'Vibration only', 'Silent']
const ALERTS: { id: AlertId; label: string; sound: KeyOf<number>; popup: KeyOf<boolean> }[] = [
  { id: 'short', label: 'Short eye break', sound: 'shortSound', popup: 'shortPopup' },
  { id: 'long', label: 'Long break', sound: 'longSound', popup: 'longPopup' },
  { id: 'over', label: 'Break over', sound: 'overSound', popup: 'overPopup' },
  { id: 'water', label: 'Water', sound: 'waterSound', popup: 'waterPopup' },
  { id: 'phone', label: 'Phone-use reminder', sound: 'phoneSound', popup: 'phonePopup' },
]

const hm = (v: string) => (v ? fmtClock(new Date(`2000-01-01T${v}`).getTime()) : '--:--')
const toHm = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
const fromHm = (v: string) => Number(v.slice(0, 2)) * 60 + Number(v.slice(3, 5))
const n = (v: number) => (Number.isNaN(v) ? '?' : v)
const how = (sound: number, popup: boolean) => `${popup ? 'Pop-up' : 'Quiet'}, ${(SOUNDS[sound] ?? SOUNDS[0]).toLowerCase()}`

/** A collapsible card: its title and a one-line summary stay visible when it is closed. */
function Section({ title, summary, children }: { title: string; summary: string; children: ReactNode }) {
  return (
    <details className="card">
      <summary><span><b>{title}</b><small>{summary}</small></span></summary>
      {children}
    </details>
  )
}

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
  const setDay = (k: 'dayWake' | 'daySleep' | 'dayOn', i: number, v: number) =>
    setDraft(d => ({ ...d, [k]: d[k].map((x, j) => (j === i ? v : x)) }))
  // Gives the days in `to` this day's hours, and switches them on.
  const copyDay = (from: number, to: number[]) =>
    setDraft(d => ({
      ...d,
      dayWake: d.dayWake.map((x, j) => (to.includes(j) ? d.dayWake[from] : x)),
      daySleep: d.daySleep.map((x, j) => (to.includes(j) ? d.daySleep[from] : x)),
      dayOn: d.dayOn.map((x, j) => (to.includes(j) ? 1 : x)),
    }))
  const daysOn = draft.dayOn.reduce((a, b) => a + b, 0)
  const dayText = (i: number) => (draft.dayOn[i] ? `${hm(toHm(draft.dayWake[i]))} to ${hm(toHm(draft.daySleep[i]))}` : 'off')
  const same = (ids: number[]) => ids.every(i => dayText(i) === dayText(ids[0]))

  const hows = ALERTS.map(a => how(draft[a.sound], draft[a.popup]))
  const summary = {
    day: same([0, 1, 2, 3, 4, 5, 6]) ? `Every day ${dayText(0)}`
      : same([0, 1, 2, 3, 4]) && same([5, 6]) ? `Weekdays ${dayText(0)}, weekends ${dayText(5)}`
      : `Each day has its own hours${daysOn < 7 ? `, ${7 - daysOn} off` : ''}`,
    eyes: draft.breaksOn
      ? `Every ${n(draft.shortEveryMin)} min for ${n(draft.shortSec)} s, long break every ${n(draft.longEveryN * (draft.shortEveryMin || 20))} min`
      : 'Off',
    phone: draft.screenOn ? `On, every ${n(draft.screenLimitMin)} min in a row` : 'Off',
    water: !draft.waterOn ? 'Off'
      : `${draft.smartWater ? 'Smart goal' : `${n(draft.customGoal)} glasses`}, ${draft.waterCustom ? `${hm(draft.waterStart)} to ${hm(draft.waterStop)}` : 'automatic hours'}`,
    lock: draft.wakeScreen ? 'Wake screen is on' : 'Wake screen is off',
    alerts: hows.every(h => h === hows[0]) ? hows[0] : 'Different for each alert',
  }

  return (
    <>
      <h1>Settings</h1>

      <Section title="Day" summary={summary.day}>
        {WEEK.map((name, i) => (
          <details key={name} className="alert">
            <summary><span><b>{name}</b><small>{draft.dayOn[i] ? dayText(i) : 'No reminders'}</small></span></summary>
            <div className="opts">
              <label className="check">
                <input type="checkbox" checked={draft.dayOn[i] === 1} disabled={draft.dayOn[i] === 1 && daysOn === 1}
                  onChange={e => setDay('dayOn', i, e.target.checked ? 1 : 0)} />
                Reminders on this day
              </label>
              {draft.dayOn[i] === 1 && (
                <>
                  <label className="field">
                    Wake
                    <input type="time" value={toHm(draft.dayWake[i])}
                      onChange={e => { if (e.target.value) setDay('dayWake', i, fromHm(e.target.value)) }} />
                  </label>
                  <label className="field">
                    Sleep
                    <input type="time" value={toHm(draft.daySleep[i])}
                      onChange={e => { if (e.target.value) setDay('daySleep', i, fromHm(e.target.value)) }} />
                  </label>
                  <p className="muted" id={`copy-${i}`}>Copy these hours to</p>
                  <div className="copy" role="group" aria-labelledby={`copy-${i}`}>
                    <button onClick={() => copyDay(i, [0, 1, 2, 3, 4])}>Mon to Fri</button>
                    <button onClick={() => copyDay(i, [5, 6])}>Sat and Sun</button>
                    <button onClick={() => copyDay(i, [0, 1, 2, 3, 4, 5, 6])}>All days</button>
                  </div>
                </>
              )}
              {draft.dayOn[i] === 1 && daysOn === 1 && <p className="muted">At least one day stays on.</p>}
            </div>
          </details>
        ))}
      </Section>

      <Section title="Eye breaks" summary={summary.eyes}>
        {check('Eye breaks on', 'breaksOn')}
        <div className="grid">
          {num('Short break every (min)', 'shortEveryMin', 1, 60)}
          {num('Short break length (s)', 'shortSec', 10, 60)}
          {num('Every Nth break is long', 'longEveryN', 2, 99)}
          {num('Long break length (min)', 'longMin', 1, 15)}
        </div>
        <p className="muted">A long break comes about every {n(draft.longEveryN * (draft.shortEveryMin || 20))} min.</p>
      </Section>

      <Section title="Phone use" summary={summary.phone}>
        {check('Remind after long phone use', 'screenOn')}
        {draft.screenOn && num('Minutes in a row', 'screenLimitMin', 1, 60)}
        <p className="muted">Counts only this phone, while it is unlocked. A screen-off of 1 minute or more starts a new count.</p>
      </Section>

      <Section title="Water" summary={summary.water}>
        {check('Water reminders on', 'waterOn')}
        {check('Smart water (season goal)', 'smartWater')}
        {check('Choose water start and stop time', 'waterCustom')}
        <div className="grid">
          {draft.waterCustom && time('Water starts', 'waterStart')}
          {draft.waterCustom && time('Water stops', 'waterStop')}
          {!draft.smartWater && num('Custom goal (glasses)', 'customGoal', 1, 20)}
          {!draft.smartWater && num('Every (min)', 'customEveryMin', 1, 180)}
          {num('Glass size (ml)', 'glassMl', 100, 500)}
          {!draft.waterCustom && num('Stop before sleep (min)', 'stopBeforeSleepMin', 0, 180)}
        </div>
        <p className="muted">{draft.waterCustom ? 'Reminders run between these times, inside your wake hours.' : 'Reminders start 10 min after you wake and stop before sleep.'}</p>
      </Section>

      <Section title="Lock screen" summary={summary.lock}>
        {check('Wake screen for reminders', 'wakeScreen')}
        <p className="muted">Shows the break screen on the lock screen.</p>
      </Section>

      <Section title="Alerts" summary={summary.alerts}>
        {ALERTS.map(a => (
          <details key={a.id} className="alert">
            <summary><span><b>{a.label}</b><small>{how(draft[a.sound], draft[a.popup])}</small></span></summary>
            <div className="opts">
              <label className="field">
                Sound and vibration
                <select value={draft[a.sound]} onChange={e => set(a.sound, Number(e.target.value))}>
                  {SOUNDS.map((name, i) => <option key={name} value={i}>{name}</option>)}
                </select>
              </label>
              <label className="field">
                How it appears
                <select value={draft[a.popup] ? 'pop' : 'quiet'} onChange={e => set(a.popup, e.target.value === 'pop')}>
                  <option value="pop">Pop-up banner</option>
                  <option value="quiet">Quiet, no pop-up</option>
                </select>
              </label>
              <button onClick={() => { DigitalCare.testAlert({ alert: a.id, sound: draft[a.sound], popup: draft[a.popup] }).catch(() => {}) }}>
                Send a test alert
              </button>
            </div>
          </details>
        ))}
        <p className="muted">Android, Do Not Disturb and your phone’s own notification page can still mute an alert.</p>
        <button onClick={() => { DigitalCare.openSystemSettings({ page: 'channel' }).catch(() => {}) }}>Open system sound settings</button>
      </Section>

      <p className="muted">General guidance only. If you have a kidney or heart condition, follow your doctor’s advice on water.</p>

      <div className="row">
        <button className="primary" onClick={() => { act(DigitalCare.saveSettings(draft)); go('home') }}>Save</button>
        <button onClick={() => go('home')}>Cancel</button>
      </div>
    </>
  )
}
