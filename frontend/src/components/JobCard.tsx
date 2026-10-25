import type { EvaluationResponse } from '../types'

interface ScoreItem {
  label: string
  name: string
  value: number
  excluded: boolean
}

function JobCard({ result, rank }: { result: EvaluationResponse; rank?: number }) {
  const scoreItems: ScoreItem[] = [
    { label: '기술', name: '필수 기술', value: result.skillScore, excluded: result.skillExcluded },
    { label: '경험', name: '프로젝트 경험', value: result.experienceScore, excluded: result.experienceExcluded },
    { label: '우대', name: '우대사항', value: result.preferenceScore, excluded: result.preferenceExcluded },
    { label: '자격증', name: '자격증', value: result.certificateScore, excluded: result.certificateExcluded },
  ]
  const isUnfit = result.verdict === 'UNFIT'
  const isInsufficientInfo = result.verdict === 'INSUFFICIENT_INFO'
  // 총점에 반영되지 않는(UNFIT) 또는 비교할 정보 자체가 부족한(INSUFFICIENT_INFO) 경우, 세부 점수는 참고용임을 표시한다.
  const isReferenceOnly = isUnfit || isInsufficientInfo
  // 기술·경험이 모두 평가 제외되면 우대사항·자격증만으로 총점이 계산되므로, 다른 공고와 기준이 다름을 안내한다.
  // 백엔드의 제외 규칙(현재는 둘 다 requiredSkills 기준)에 기대지 않도록 두 플래그를 직접 확인한다.
  // 4개 항목이 모두 제외된 경우는 INSUFFICIENT_INFO로 판정되므로 FIT일 때만 표시한다.
  const isScopeLimited = result.verdict === 'FIT' && result.skillExcluded && result.experienceExcluded
  const evaluatedItemNames = scoreItems.filter((item) => !item.excluded).map((item) => item.name)

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

      {isScopeLimited && (
        <div className="scope-limited-notice">
          평가 범위 제한: {evaluatedItemNames.join('·')}만 평가됨 — 기술·경험 요건은 자동 평가되지 않았으니 공고 원문을 확인하세요
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
