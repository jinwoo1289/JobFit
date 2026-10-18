import type { EvaluationResponse } from '../types'

interface ScoreItem {
  label: string
  value: number
  excluded: boolean
}

function JobCard({ result, rank }: { result: EvaluationResponse; rank?: number }) {
  const scoreItems: ScoreItem[] = [
    { label: '기술', value: result.skillScore, excluded: result.skillExcluded },
    { label: '경험', value: result.experienceScore, excluded: result.experienceExcluded },
    { label: '우대', value: result.preferenceScore, excluded: result.preferenceExcluded },
    { label: '자격증', value: result.certificateScore, excluded: result.certificateExcluded },
  ]
  const isUnfit = result.verdict === 'UNFIT'
  const isInsufficientInfo = result.verdict === 'INSUFFICIENT_INFO'
  // 총점에 반영되지 않는(UNFIT) 또는 비교할 정보 자체가 부족한(INSUFFICIENT_INFO) 경우, 세부 점수는 참고용임을 표시한다.
  const isReferenceOnly = isUnfit || isInsufficientInfo

  return (
    <article className={`job-card ${isReferenceOnly ? 'job-card-unfit' : ''}`}>
      <header className="job-card-header">
        {rank !== undefined && <span className="job-card-rank">{rank}</span>}
        <div>
          <p className="job-card-company">{result.companyName}</p>
          <h3 className="job-card-title">{result.title}</h3>
        </div>
        <span
          className={`badge ${isUnfit ? 'badge-unfit' : isInsufficientInfo ? 'badge-insufficient' : 'badge-fit'}`}
        >
          {isInsufficientInfo ? '정보 부족' : result.verdict}
        </span>
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

      <div className="job-card-scores-header">
        <span className="job-card-scores-title">세부 점수</span>
        {isReferenceOnly && <span className="score-reference-label">참고용</span>}
      </div>
      <div className={`job-card-scores ${isReferenceOnly ? 'job-card-scores-unfit' : ''}`}>
        {scoreItems.map((item) => (
          <div key={item.label} className="score-item">
            <span className="score-item-label">{item.label}</span>
            {item.excluded ? (
              <>
                <span className="score-item-value score-item-value-excluded">-</span>
                <span className="score-item-excluded-note">평가 제외</span>
              </>
            ) : (
              <span className="score-item-value">{item.value.toFixed(1)}</span>
            )}
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
