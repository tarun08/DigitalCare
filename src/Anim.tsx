/** Built-in animation; a static frame under prefers-reduced-motion. */
export function Anim({ name, alt }: { name: 'eye' | 'stretch' | 'drink'; alt: string }) {
  return (
    <picture>
      <source srcSet={`anim/${name}.png`} media="(prefers-reduced-motion: reduce)" />
      <img src={`anim/${name}.webp`} alt={alt} width="160" height="160" />
    </picture>
  )
}
