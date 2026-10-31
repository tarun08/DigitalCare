import { useState } from 'react'
import { EyeCare, type State, type SystemPage } from './plugin'

const DISMISSED = 'eyecare.vivoStepsDismissed'

function readDismissed(): boolean {
  try { return localStorage.getItem(DISMISSED) === '1' } catch { return false }
}

export function SetupCard({ state }: { state: State }) {
  const [dismissed, setDismissed] = useState(readDismissed)
  const open = (page: SystemPage) => { EyeCare.openSystemSettings({ page }).catch(() => {}) }
  const rows: [string, boolean, SystemPage][] = [
    ['Notifications', state.setup.notifications, 'channel'],
    ['Battery optimization off', state.setup.battery, 'battery'],
  ]
  if (state.settings.wakeScreen) rows.push(['Full-screen alerts', state.setup.fullScreen, 'fullScreen'])
  const vivo = state.manufacturer === 'vivo'
  if (rows.every(([, ok]) => ok) && (!vivo || dismissed)) return null

  const dismiss = () => {
    try { localStorage.setItem(DISMISSED, '1') } catch { /* storage blocked: hide for this session only */ }
    setDismissed(true)
  }

  return (
    <section className="card setup">
      <h2>Setup</h2>
      <ul className="checks">
        {rows.map(([label, ok, page]) => (
          <li key={page}>
            <span>{label}</span>
            {ok ? <span className="ok" aria-label="done">✓</span> : <button onClick={() => open(page)}>Fix</button>}
          </li>
        ))}
      </ul>
      {vivo ? !dismissed && (
        <>
          <h3>Vivo phones also need</h3>
          <ol>
            <li>Settings → Battery → Background power consumption management → EyeCare → allow high background usage / don't restrict.</li>
            <li>Settings → Apps → Autostart (on some versions: i Manager → App manager → Autostart) → EyeCare on.</li>
            <li>App info → Permissions → "Display on lock screen" and "Display pop-up windows while running in background" on (needed for Wake screen).</li>
            <li>Recent apps → lock the EyeCare card.</li>
          </ol>
          <p className="muted">Names vary by phone version.</p>
          <div className="row">
            <button onClick={() => open('appDetails')}>Open app info</button>
            <button onClick={dismiss}>Done, hide these</button>
          </div>
        </>
      ) : (
        <p><a href="https://dontkillmyapp.com/">Keep reminders alive: dontkillmyapp.com</a></p>
      )}
    </section>
  )
}
