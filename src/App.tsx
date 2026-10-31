import { useCallback, useEffect, useState } from 'react'
import { EyeCare, type Screen, type State } from './plugin'
import { BreakScreen } from './BreakScreen'
import { Home } from './Home'
import { SettingsScreen } from './SettingsScreen'
import { WaterScreen } from './WaterScreen'

export type View = Screen | 'settings'
export interface ScreenProps { state: State; act: (p: Promise<State>) => void; go: (v: View) => void }

export function App() {
  const [state, setState] = useState<State | null>(null)
  const [view, setView] = useState<View>('home')
  const [error, setError] = useState<string | null>(null)

  const act = useCallback((p: Promise<State>) => {
    p.then(
      s => { setState(s); setError(null) },
      (e: unknown) => setError(e instanceof Error ? e.message : String(e)),
    )
  }, [])

  useEffect(() => {
    const refresh = () => act(EyeCare.getState())
    refresh()
    EyeCare.getLaunchScreen().then(r => setView(r.screen), () => {})
    const handle = EyeCare.addListener('launchScreen', e => { setView(e.screen); refresh() })
    const onVisible = () => { if (document.visibilityState === 'visible') refresh() }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      handle.then(h => h.remove())
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [act])

  // Alarms change state in the background; refresh once the next event time passes so an open screen stays true.
  useEffect(() => {
    if (!state) return
    const now = Date.now()
    const next = Math.min(...[state.nextBreakAt, state.breakEndAt, state.nextWaterAt, state.pausedUntil, state.nextDayStartAt]
      .filter((t): t is number => t !== null && t > now))
    if (!Number.isFinite(next)) return
    const id = setTimeout(() => act(EyeCare.getState()), next - now + 1500)
    return () => clearTimeout(id)
  }, [state, act])

  if (!state) return <main>{error ? <p className="error" role="alert">{error}</p> : <p>Loading…</p>}</main>
  const props: ScreenProps = { state, act, go: setView }
  return (
    <main>
      {error && <p className="error" role="alert">{error}</p>}
      {view === 'short' || view === 'long' ? <BreakScreen {...props} type={view} />
        : view === 'water' ? <WaterScreen {...props} />
        : view === 'settings' ? <SettingsScreen {...props} />
        : <Home {...props} />}
    </main>
  )
}
