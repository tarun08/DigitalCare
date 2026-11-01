import { useState, type ReactNode } from 'react'
import { DigitalCare, type AlertId, type Settings } from './plugin'
import type { ScreenProps } from './App'
import { text, usePageLanguage } from './text'
import { fmtClock } from './time'

type KeyOf<T> = { [K in keyof Settings]: Settings[K] extends T ? K : never }[keyof Settings]

// `full` is the "Full screen" choice; Break over has no screen to open.
const ALERTS: { id: AlertId; sound: KeyOf<number>; popup: KeyOf<boolean>; full?: KeyOf<boolean> }[] = [
  { id: 'short', sound: 'shortSound', popup: 'shortPopup', full: 'shortFull' },
  { id: 'long', sound: 'longSound', popup: 'longPopup', full: 'longFull' },
  { id: 'over', sound: 'overSound', popup: 'overPopup' },
  { id: 'water', sound: 'waterSound', popup: 'waterPopup', full: 'waterFull' },
  { id: 'phone', sound: 'phoneSound', popup: 'phonePopup', full: 'phoneFull' },
]

const hm = (v: string) => (v ? fmtClock(new Date(`2000-01-01T${v}`).getTime()) : '--:--')
const toHm = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
const fromHm = (v: string) => Number(v.slice(0, 2)) * 60 + Number(v.slice(3, 5))
const n = (v: number) => (Number.isNaN(v) ? '?' : v)

// Edits not saved yet. They survive a reminder opening its own screen over Settings; Save and Cancel clear them.
let unsaved: Settings | null = null

/** A collapsible card: its title and a short summary (one line per string) stay visible when it is closed. */
function Section({ title, summary, children }: { title: ReactNode; summary: ReactNode | ReactNode[]; children: ReactNode }) {
  return (
    <details className="card">
      <summary><span><b>{title}</b>{[summary].flat().map((line, i) => <small key={i}>{line}</small>)}</span></summary>
      {children}
    </details>
  )
}

export function SettingsScreen({ state, act, go }: ScreenProps) {
  const [draft, setDraftState] = useState<Settings>(() => unsaved ?? state.settings)
  const setDraft = (change: (d: Settings) => Settings) => setDraftState(d => (unsaved = change(d)))
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setDraft(d => ({ ...d, [k]: v }))
  const t = text(draft.language).settings // the language you pick shows at once; Cancel puts it back
  usePageLanguage(draft.language, state.settings.language)

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
  const dayText = (i: number) => (draft.dayOn[i] ? t.range(hm(toHm(draft.dayWake[i])), hm(toHm(draft.daySleep[i]))) : t.off)
  const same = (ids: number[]) => ids.every(i => dayText(i) === dayText(ids[0]))

  const full = (a: (typeof ALERTS)[number]) => !!a.full && draft[a.full]
  const how = (sound: number, popup: boolean, isFull: boolean) =>
    `${isFull ? t.kindFull : popup ? t.kindPop : t.kindQuiet}, ${(t.sounds[sound] ?? t.sounds[0]).toLowerCase()}`
  const hows = ALERTS.map(a => how(draft[a.sound], draft[a.popup], full(a)))
  const summary = {
    language: draft.language === 'hi' ? <span lang="hi" className="hi">हिन्दी</span> : 'English',
    day: same([0, 1, 2, 3, 4, 5, 6]) ? t.dayEvery(dayText(0))
      : same([0, 1, 2, 3, 4]) && same([5, 6]) ? t.dayWeek(dayText(0), dayText(5))
      : t.dayEach(7 - daysOn),
    eyes: draft.breaksOn ? t.eyesSummary(n(draft.shortEveryMin), n(draft.shortSec), n(draft.longEveryMin)) : t.offCap,
    phone: draft.screenOn ? t.phoneSummary(n(draft.screenLimitMin)) : t.offCap,
    water: !draft.waterOn ? t.offCap
      : `${draft.smartWater ? t.waterSmart : t.waterGoal(n(draft.customGoal))}, ${draft.waterCustom ? t.range(hm(draft.waterStart), hm(draft.waterStop)) : t.waterAuto}`,
    lock: draft.wakeScreen ? t.lockOn : t.lockOff,
    alerts: hows.every(h => h === hows[0]) ? hows[0] : t.alertsDiffer,
  }

  return (
    <>
      <h1>{t.title}</h1>

      <Section title={<>Language · <span lang="hi" className="hi">भाषा</span></>} summary={summary.language}>
        <label className="field">
          {t.languageField}
          <select value={draft.language} onChange={e => set('language', e.target.value as Settings['language'])}>
            <option value="en">English</option>
            <option value="hi">हिन्दी</option>
          </select>
        </label>
        <p className="muted">The whole app follows this: the notifications, the reminder screens and every setting.</p>
        <p className="muted hi" lang="hi">पूरा ऐप इसी भाषा में दिखेगा: सूचनाएँ, रिमाइंडर स्क्रीन और सभी सेटिंग्स।</p>
      </Section>

      <Section title={t.cardEyes} summary={[summary.eyes, summary.day]}>
        {check(t.eyesOn, 'breaksOn')}
        <div className="grid">
          {num(t.shortEvery, 'shortEveryMin', 1, 60)}
          {num(t.shortLen, 'shortSec', 10, 60)}
          {num(t.longEvery, 'longEveryMin', 2 * (draft.shortEveryMin || 20), 1440)}
          {num(t.longLen, 'longMin', 1, 15)}
        </div>
        <p className="muted">{t.longNote}</p>
        <h3>{t.hoursHeading}</h3>
        {t.days.map((name, i) => (
          <details key={name} className="alert">
            <summary><span><b>{name}</b><small>{draft.dayOn[i] ? dayText(i) : t.noReminders}</small></span></summary>
            <div className="opts">
              <label className="check">
                <input type="checkbox" checked={draft.dayOn[i] === 1} disabled={draft.dayOn[i] === 1 && daysOn === 1}
                  onChange={e => setDay('dayOn', i, e.target.checked ? 1 : 0)} />
                {t.dayOn}
              </label>
              {draft.dayOn[i] === 1 && (
                <>
                  <label className="field">
                    {t.wake}
                    <input type="time" value={toHm(draft.dayWake[i])}
                      onChange={e => { if (e.target.value) setDay('dayWake', i, fromHm(e.target.value)) }} />
                  </label>
                  <label className="field">
                    {t.sleep}
                    <input type="time" value={toHm(draft.daySleep[i])}
                      onChange={e => { if (e.target.value) setDay('daySleep', i, fromHm(e.target.value)) }} />
                  </label>
                  <p className="muted" id={`copy-${i}`}>{t.copyTo}</p>
                  <div className="copy" role="group" aria-labelledby={`copy-${i}`}>
                    <button onClick={() => copyDay(i, [0, 1, 2, 3, 4])}>{t.monFri}</button>
                    <button onClick={() => copyDay(i, [5, 6])}>{t.satSun}</button>
                    <button onClick={() => copyDay(i, [0, 1, 2, 3, 4, 5, 6])}>{t.allDays}</button>
                  </div>
                </>
              )}
              {draft.dayOn[i] === 1 && daysOn === 1 && <p className="muted">{t.oneDay}</p>}
            </div>
          </details>
        ))}
        <p className="muted">{t.hoursNote}</p>
      </Section>

      <Section title={t.cardPhone} summary={summary.phone}>
        {check(t.phoneOn, 'screenOn')}
        {draft.screenOn && num(t.minutesRow, 'screenLimitMin', 1, 60)}
        <p className="muted">{t.phoneNote}</p>
      </Section>

      <Section title={t.cardWater} summary={summary.water}>
        {check(t.waterOn, 'waterOn')}
        {check(t.smart, 'smartWater')}
        {check(t.custom, 'waterCustom')}
        <div className="grid">
          {draft.waterCustom && time(t.waterStarts, 'waterStart')}
          {draft.waterCustom && time(t.waterStops, 'waterStop')}
          {!draft.smartWater && num(t.customGoal, 'customGoal', 1, 20)}
          {!draft.smartWater && num(t.every, 'customEveryMin', 1, 180)}
          {num(t.glassSize, 'glassMl', 100, 500)}
          {!draft.waterCustom && num(t.stopBefore, 'stopBeforeSleepMin', 0, 180)}
        </div>
        <p className="muted">{draft.waterCustom ? t.waterNoteCustom : t.waterNoteAuto}</p>
      </Section>

      <Section title={t.cardLock} summary={summary.lock}>
        {check(t.wakeScreen, 'wakeScreen')}
        <p className="muted">{t.wakeNote}</p>
      </Section>

      <Section title={t.cardAlerts} summary={summary.alerts}>
        {ALERTS.map(a => (
          <details key={a.id} className="alert">
            <summary><span><b>{t.alertName[a.id]}</b><small>{how(draft[a.sound], draft[a.popup], full(a))}</small></span></summary>
            <div className="opts">
              <label className="field">
                {t.soundField}
                <select value={draft[a.sound]} onChange={e => set(a.sound, Number(e.target.value))}>
                  {t.sounds.map((name, i) => <option key={name} value={i}>{name}</option>)}
                </select>
              </label>
              <label className="field">
                {t.howField}
                <select value={full(a) ? 'full' : draft[a.popup] ? 'pop' : 'quiet'}
                  onChange={e => { if (a.full) set(a.full, e.target.value === 'full'); set(a.popup, e.target.value !== 'quiet') }}>
                  {a.full && <option value="full">{t.optFull}</option>}
                  <option value="pop">{t.optPop}</option>
                  <option value="quiet">{t.optQuiet}</option>
                </select>
              </label>
              <button onClick={() => { DigitalCare.testAlert({ alert: a.id, sound: draft[a.sound], popup: draft[a.popup] }).catch(() => {}) }}>
                {t.test}
              </button>
            </div>
          </details>
        ))}
        <p className="muted">{t.fullNote}</p>
        <p className="muted">{t.muteNote}</p>
        <button onClick={() => { DigitalCare.openSystemSettings({ page: 'channel' }).catch(() => {}) }}>{t.openSound}</button>
      </Section>

      <p className="muted">{t.disclaimer}</p>

      <div className="row">
        <button className="primary" onClick={() => { act(DigitalCare.saveSettings(draft)); unsaved = null; go('home') }}>{t.save}</button>
        <button onClick={() => { unsaved = null; go('home') }}>{t.cancel}</button>
      </div>
    </>
  )
}
