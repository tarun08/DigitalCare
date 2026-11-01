import { useCallback, useEffect, useState } from 'react'
import { DigitalCare, type Screen, type State } from './plugin'
import { BreakScreen } from './BreakScreen'
import { Home } from './Home'
import { SettingsScreen } from './SettingsScreen'
import { usePageLanguage } from './text'
import { WaterScreen } from './WaterScreen'

export type View = Screen | 'settings'
export interface ScreenProps { state: State; act: (p: Promise<State>) => void; go: (v: View) => void; done: () => void }

export function App() {
  const [state, setState] = useState<State | null>(null)
  const [view, setView] = useState<View>('home')
  const [error, setError] = useState<string | null>(null)
  usePageLanguage(state?.settings.language ?? 'en')

  const act = useCallback((p: Promise<State>) => {
    p.then(
      s => { setState(s); setError(null) },
      (e: unknown) => setError(e instanceof Error ? e.message : String(e)),
    )
  }, [])

  // A reminder screen is finished: back to Home, and the phone returns to the app the reminder opened over (if it did).
  const done = useCallback(() => { setView('home'); DigitalCare.leave().catch(() => {}) }, [])

  useEffect(() => {
    const refresh = () => act(DigitalCare.getState())
    refresh()
    const warm = setTimeout(refresh, 1500) // the phone-use service starts just after the first state; read it once more
    DigitalCare.getLaunchScreen().then(r => setView(r.screen), () => {})
    const handles = [
      DigitalCare.addListener('launchScreen', e => { setView(e.screen); refresh() }),
      // Native resume, not visibilitychange: it also fires after the permission and battery dialogs.
      DigitalCare.addListener('appResume', refresh),
    ]
    return () => { clearTimeout(warm); handles.forEach(h => h.then(x => x.remove())) }
  }, [act])

  // Alarms change state in the background; refresh once the next event time passes so an open screen stays true.
  useEffect(() => {
    if (!state) return
    const now = Date.now()
    const next = Math.min(...[state.nextBreakAt, state.breakEndAt, state.nextWaterAt, state.pausedUntil, state.nextDayStartAt]
      .filter((t): t is number => t !== null && t > now))
    if (!Number.isFinite(next)) return
    const id = setTimeout(() => act(DigitalCare.getState()), next - now + 1500)
    return () => clearTimeout(id)
  }, [state, act])

  if (!state) return <main>{error ? <p className="error" role="alert">{error}</p> : <p>Loading…</p>}</main>
  const props: ScreenProps = { state, act, go: setView, done }
  return (
    <main>
      {error && <p className="error" role="alert">{error}</p>}
      {view === 'short' || view === 'long' || view === 'phone' ? <BreakScreen {...props} type={view} />
        : view === 'water' ? <WaterScreen {...props} />
        : view === 'settings' ? <SettingsScreen {...props} />
        : <Home {...props} />}
    </main>
  )
}
