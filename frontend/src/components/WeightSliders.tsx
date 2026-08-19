import type { Weights } from '../types'

interface WeightItem {
  key: keyof Weights
  label: string
}

const WEIGHT_ITEMS: WeightItem[] = [
  { key: 'skillWeight', label: '기술' },
  { key: 'experienceWeight', label: '경험' },
  { key: 'preferenceWeight', label: '우대' },
  { key: 'certificateWeight', label: '자격증' },
]

interface WeightSlidersProps {
  weights: Weights
  onChange: (key: keyof Weights, value: number) => void
}

function WeightSliders({ weights, onChange }: WeightSlidersProps) {
  const total = WEIGHT_ITEMS.reduce((sum, item) => sum + weights[item.key], 0)
  const isValid = total === 100

  return (
    <section className="weights-panel">
      <div className="weights-header">
        <h2>가중치 설정</h2>
        <span className={`weights-total ${isValid ? 'valid' : 'invalid'}`}>
          합계 {total} / 100
        </span>
      </div>
      <div className="weights-grid">
        {WEIGHT_ITEMS.map((item) => (
          <label key={item.key} className="weight-slider">
            <div className="weight-slider-label">
              <span>{item.label}</span>
              <span>{weights[item.key]}</span>
            </div>
            <input
              type="range"
              min={0}
              max={100}
              value={weights[item.key]}
              onChange={(e) => onChange(item.key, Number(e.target.value))}
            />
          </label>
        ))}
      </div>
    </section>
  )
}

export default WeightSliders
