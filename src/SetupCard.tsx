import { useState } from 'react'
import { DigitalCare, type State, type SystemPage } from './plugin'
import { text } from './text'

const DISMISSED = 'digitalcare.vivoStepsDismissed'

function readDismissed(): boolean {
  try { return localStorage.getItem(DISMISSED) === '1' } catch { return false }
}

export function SetupCard({ state }: { state: State }) {
  const [dismissed, setDismissed] = useState(readDismissed)
  const s = state.settings
  const t = text(s.language).setup
  const open = (page: SystemPage) => { DigitalCare.openSystemSettings({ page }).catch(() => {}) }
  const rows: [string, boolean, SystemPage][] = [
    [t.notifications, state.setup.notifications, 'channel'],
    [t.battery, state.setup.battery, 'battery'],
  ]
  if (s.screenOn) rows.push([state.setup.phoneUse ? t.phoneRunning : t.phoneStopped, state.setup.phoneUse, 'battery'])
  const anyFull = s.shortFull || s.longFull || s.waterFull || s.phoneFull
  if (s.wakeScreen || anyFull) rows.push([t.fullScreen, state.setup.fullScreen, 'fullScreen'])
  if (anyFull) rows.push([t.overlay, state.setup.overlay, 'overlay'])
  const vivo = state.manufacturer === 'vivo'
  if (rows.every(([, ok]) => ok) && (!vivo || dismissed)) return null

  const dismiss = () => {
    try { localStorage.setItem(DISMISSED, '1') } catch { /* storage blocked: hide for this session only */ }
    setDismissed(true)
  }

  return (
    <section className="card setup">
      <h2>{t.title}</h2>
      <ul className="checks">
        {rows.map(([label, ok, page]) => (
          <li key={label}>
            <span>{label}</span>
            {ok ? <span className="ok" aria-label={t.done}>✓</span> : <button onClick={() => open(page)}>{t.fix}</button>}
          </li>
        ))}
      </ul>
      {vivo ? !dismissed && (
        <>
          <h3>{t.vivoTitle}</h3>
          <ol>
            <li>{t.vivo1}</li>
            <li>{t.vivo2}</li>
            <li>{t.vivo3}</li>
            <li>{t.vivo4}</li>
          </ol>
          <p className="muted">{t.namesVary}</p>
          <div className="row">
            <button onClick={() => open('appDetails')}>{t.appInfo}</button>
            <button onClick={dismiss}>{t.hide}</button>
          </div>
        </>
      ) : (
        <p><a href="https://dontkillmyapp.com/">{t.keepAlive}</a></p>
      )}
    </section>
  )
}
