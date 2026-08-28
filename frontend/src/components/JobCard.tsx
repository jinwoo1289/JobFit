import type { EvaluationResponse } from '../types'

interface ScoreItem {
  label: string
  value: number
}

function JobCard({ result, rank }: { result: EvaluationResponse; rank?: number }) {
  const scoreItems: ScoreItem[] = [
    { label: '기술', value: result.skillScore },
    { label: '경험', value: result.experienceScore },
    { label: '우대', value: result.preferenceScore },
    { label: '자격증', value: result.certificateScore },
  ]
  const isUnfit = result.verdict === 'UNFIT'

  return (
    <article className={`job-card ${isUnfit ? 'job-card-unfit' : ''}`}>
      <header className="job-card-header">
        {rank !== undefined && <span className="job-card-rank">{rank}</span>}
        <div>
          <p className="job-card-company">{result.companyName}</p>
          <h3 className="job-card-title">{result.title}</h3>
        </div>
        <span className={`badge ${isUnfit ? 'badge-unfit' : 'badge-fit'}`}>{result.verdict}</span>
      </header>

      {result.verdict === 'UNFIT' && result.failedReasons.length > 0 && (
        <div className="reasoning-block failed-reasons-block">
          <h4 className="reasoning-title reasoning-title-negative">지원 불가 사유</h4>
          <ul>
            {result.failedReasons.map((reason, i) => (
              <li key={i}>{reason}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="job-card-total">
        <span>종합 점수</span>
        <strong>{result.totalScore.toFixed(1)}</strong>
      </div>

      <div className="job-card-scores">
        {scoreItems.map((item) => (
          <div key={item.label} className="score-item">
            <span className="score-item-label">{item.label}</span>
            <span className="score-item-value">{item.value.toFixed(1)}</span>
          </div>
        ))}
      </div>

      {result.reasoning.matchedPoints.length > 0 && (
        <div className="reasoning-block">
          <h4 className="reasoning-title reasoning-title-positive">적합한 점</h4>
          <ul>
            {result.reasoning.matchedPoints.map((point, i) => (
              <li key={i}>{point}</li>
            ))}
          </ul>
        </div>
      )}

      {result.reasoning.gapPoints.length > 0 && (
        <div className="reasoning-block">
          <h4 className="reasoning-title reasoning-title-negative">부족한 점</h4>
          <ul>
            {result.reasoning.gapPoints.map((point, i) => (
              <li key={i}>{point}</li>
            ))}
          </ul>
        </div>
      )}

      <a className="job-card-link" href={result.originalUrl} target="_blank" rel="noreferrer">
        원문 보기 →
      </a>
    </article>
  )
}

export default JobCard
