import { DigitalCare } from './plugin'
import type { ScreenProps } from './App'
import { Anim } from './Anim'
import { text } from './text'

export function WaterScreen({ state, act, done }: ScreenProps) {
  const t = text(state.settings.language).reminder
  return (
    <section className="card center">
      <h1>{t.water}</h1>
      <Anim name="drink" alt={t.glassAlt} />
      <p className="big">{t.glasses(state.glasses, state.goal)}</p>
      <div className="row">
        <button className="primary" onClick={() => act(DigitalCare.logGlass())}>{t.drank}</button>
        <button onClick={done}>{t.done}</button>
      </div>
    </section>
  )
}
