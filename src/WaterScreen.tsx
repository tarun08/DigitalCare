import { DigitalCare } from './plugin'
import type { ScreenProps } from './App'
import { Anim } from './Anim'

export function WaterScreen({ state, act, go }: ScreenProps) {
  return (
    <section className="card center">
      <h1>Drink water</h1>
      <Anim name="drink" alt="Glass of water" />
      <p className="big">{state.glasses} / {state.goal} glasses</p>
      <div className="row">
        <button className="primary" onClick={() => act(DigitalCare.logGlass())}>Drank 1 glass</button>
        <button onClick={() => go('home')}>Done</button>
      </div>
    </section>
  )
}
