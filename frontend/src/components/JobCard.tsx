import { useState } from 'react'
import type { EvaluationResponse } from '../types'

interface ScoreItem {
  label: string
  name: string
  value: number
  excluded: boolean
  // 점수 옆에 보여줄 개수 근거 (예: "필수 기술 1개 중 0개 보유")
  detail?: string
}

interface JobCardProps {
  result: EvaluationResponse
  rank?: number
  onToggleApplied: (nextApplied: boolean) => void
  isApplyPending: boolean
  applyError?: string
}

// "마감 11/17 (D-15)" / "마감 10/25 (종료)" / "채용시 마감".
// 남은 날 수(daysUntilDeadline)와 마감 여부(expired)는 서버 Clock 기준 값을 그대로 쓰고, 브라우저 시간으로 계산하지 않는다.
// 날짜는 "2026-11-17T23:59:59" 문자열에서 시간대 변환 없이 월/일만 꺼낸다.
function formatDeadline(result: EvaluationResponse): string {
  const match = result.expirationAt?.match(/^\d{4}-(\d{2})-(\d{2})/)
  if (!match) return '채용시 마감'
  const date = `${Number(match[1])}/${Number(match[2])}`
  if (result.expired) return `마감 ${date} (종료)`
  if (result.daysUntilDeadline === null) return `마감 ${date}`
  return `마감 ${date} (${result.daysUntilDeadline === 0 ? 'D-day' : `D-${result.daysUntilDeadline}`})`
}

// 보유하지 않은 기술이 이 개수를 넘으면 앞의 일부만 보여주고 나머지는 "외 N개 더 보기"로 접는다.
const MISSING_SKILLS_VISIBLE_COUNT = 3

function JobCard({ result, rank, onToggleApplied, isApplyPending, applyError }: JobCardProps) {
  const [isMissingExpanded, setIsMissingExpanded] = useState(false)

  const scoreItems: ScoreItem[] = [
    {
      label: '기술',
      name: '필수 기술',
      value: result.skillScore,
      excluded: result.skillExcluded,
      detail: `필수 기술 ${result.requiredSkillCount}개 중 ${result.matchedSkillCount}개 보유`,
    },
    {
      label: '경험',
      name: '프로젝트 경험',
      value: result.experienceScore,
      excluded: result.experienceExcluded,
      detail: `한 프로젝트에서 필수 기술 ${result.experienceSkillCount}개 중 ${result.experienceMatchedCount}개 사용`,
    },
    {
      label: '우대',
      name: '우대사항',
      value: result.preferenceScore,
      excluded: result.preferenceExcluded,
      detail: `우대 ${result.preferredSkillCount}개 중 ${result.preferredMatchedCount}개 보유`,
    },
    { label: '자격증', name: '자격증', value: result.certificateScore, excluded: result.certificateExcluded },
  ]
  const evaluatedItems = scoreItems.filter((item) => !item.excluded)
  const excludedItems = scoreItems.filter((item) => item.excluded)

  const isUnfit = result.verdict === 'UNFIT'
  const isInsufficientInfo = result.verdict === 'INSUFFICIENT_INFO'
  // 총점에 반영되지 않는(UNFIT) 또는 비교할 정보 자체가 부족한(INSUFFICIENT_INFO) 경우, 세부 점수는 참고용임을 표시한다.
  const isReferenceOnly = isUnfit || isInsufficientInfo
  // 부분 평가: 기술·경험이 모두 평가 제외되면 우대사항·자격증만으로 총점이 계산되므로, 다른 공고 점수와 기준이 다르다.
  // 자격증만 제외된 경우는 자격증 요건이 없는 흔한 공고라 전체 평가로 본다.
  // 백엔드의 제외 규칙(현재는 둘 다 필수 기술·택1 그룹 기준)에 기대지 않도록 두 플래그를 직접 확인한다.
  // 4개 항목이 모두 제외된 경우는 INSUFFICIENT_INFO로 판정되므로 FIT일 때만 표시한다.
  const isScopeLimited = result.verdict === 'FIT' && result.skillExcluded && result.experienceExcluded
  const evaluatedScopeLabel = `${evaluatedItems.map((item) => item.label).join('·')}만 평가`

  const hiddenMissingCount = result.missingSkills.length - MISSING_SKILLS_VISIBLE_COUNT
  const shouldCollapseMissing = result.missingSkills.length > MISSING_SKILLS_VISIBLE_COUNT
  const visibleMissingSkills =
    shouldCollapseMissing && !isMissingExpanded
      ? result.missingSkills.slice(0, MISSING_SKILLS_VISIBLE_COUNT)
      : result.missingSkills

  return (
    <article className={`job-card ${isReferenceOnly ? 'job-card-unfit' : ''}`}>
      <header className="job-card-header">
        {rank !== undefined && <span className="job-card-rank">{rank}</span>}
        <div className="job-card-heading">
          <p className="job-card-company">{result.companyName}</p>
          <h3 className="job-card-title">{result.title}</h3>
          <p className={`job-card-deadline ${result.deadlineImminent ? 'job-card-deadline-imminent' : ''}`}>
            {formatDeadline(result)}
          </p>
        </div>
        <div className="job-card-side">
          <span
            className={`badge ${isUnfit ? 'badge-unfit' : isInsufficientInfo ? 'badge-insufficient' : 'badge-fit'}`}
          >
            {isInsufficientInfo ? '정보 부족' : result.verdict}
          </span>
          <label className="applied-checkbox">
            <input
              type="checkbox"
              checked={result.applied}
              disabled={isApplyPending}
              onChange={(event) => onToggleApplied(event.target.checked)}
            />
            지원 완료
          </label>
        </div>
      </header>

      {applyError && (
        <p className="applied-error" role="alert">
          {applyError}
        </p>
      )}

      {isUnfit && result.failedReasons.length > 0 && (
        <ul className="failed-reasons" aria-label="지원 불가 사유">
          {result.failedReasons.map((reason, i) => (
            <li key={i}>{reason}</li>
          ))}
        </ul>
      )}

      {result.deadlineImminent && <div className="deadline-imminent-notice">마감이 임박한 공고입니다</div>}

      {result.notices.map((notice, i) => (
        <div key={i} className="job-notice">
          {notice}
        </div>
      ))}

      {/* UNFIT은 종합 점수가 계산되지 않으므로(항상 0) 영역 자체를 숨긴다.
          INSUFFICIENT_INFO도 0점이지만 0점짜리 공고로 오해하지 않도록 숫자 대신 "-"를 보여준다. */}
      {!isUnfit && (
        <div className={`job-card-total ${isScopeLimited ? 'job-card-total-partial' : ''}`}>
          <span className="job-card-total-label">
            종합 점수
            {isScopeLimited && <span className="score-partial-badge">부분 평가</span>}
          </span>
          {isInsufficientInfo ? (
            <strong className="job-card-total-empty" aria-label="종합 점수 없음">
              -
            </strong>
          ) : (
            <strong>
              {result.totalScore.toFixed(1)}
              {isScopeLimited && <span className="job-card-total-scope"> · {evaluatedScopeLabel}</span>}
            </strong>
          )}
        </div>
      )}
      {isScopeLimited && (
        <p className="scope-limited-hint">
          기술·경험 요건은 평가하지 못해서 다른 공고 점수와 바로 비교하기 어려워요. 원문에서 직접 확인하세요.
        </p>
      )}

      <div>
        {/* 평가된 항목이 없으면(정보 부족) 비어 있는 세부 점수 영역과 "참고용" 배지 대신 한 줄 안내만 보여준다. */}
        {evaluatedItems.length > 0 ? (
          <div className="job-card-scores-header">
            <span className="job-card-scores-title">세부 점수</span>
            {isReferenceOnly && <span className="score-reference-label">참고용</span>}
            {result.thinRequired && (
              <span className="score-reference-label">필수 요건이 적어 우대 비중을 높여 계산했어요</span>
            )}
          </div>
        ) : (
          <p className="score-empty-notice">공고에 평가할 요건 정보가 부족해요. 원문에서 직접 확인하세요.</p>
        )}
        {evaluatedItems.length > 0 && (
          <ul className={`job-card-scores ${isReferenceOnly ? 'job-card-scores-unfit' : ''}`}>
            {evaluatedItems.map((item) => (
              <li key={item.label} className="score-item">
                <span className="score-item-label">{item.label}</span>
                <span className="score-item-value">{item.value.toFixed(1)}</span>
                {item.detail && <span className="score-item-detail">{item.detail}</span>}
              </li>
            ))}
          </ul>
        )}
        {excludedItems.length > 0 && (
          <p className="score-excluded-line">평가 제외: {excludedItems.map((item) => item.label).join(', ')}</p>
        )}
      </div>

      {result.matchedSkills.length > 0 && (
        <div className="reasoning-block">
          <h4 className="reasoning-title reasoning-title-positive">적합한 점</h4>
          <ul className="matched-skills">
            {result.matchedSkills.map((matched, i) => (
              <li key={i} className="matched-skill">
                <span className="matched-skill-name">{matched.skill}</span>
                {matched.projectNames.map((projectName) => (
                  <span key={projectName} className="project-tag">
                    {projectName}
                  </span>
                ))}
              </li>
            ))}
          </ul>
        </div>
      )}

      {result.missingSkills.length > 0 && (
        <div className="reasoning-block">
          <h4 className="reasoning-title reasoning-title-negative">보유하지 않은 기술</h4>
          <ul>
            {visibleMissingSkills.map((skill, i) => (
              <li key={i}>{skill}</li>
            ))}
          </ul>
          {shouldCollapseMissing && (
            <button
              type="button"
              className="missing-skills-toggle"
              onClick={() => setIsMissingExpanded((prev) => !prev)}
              aria-expanded={isMissingExpanded}
            >
              {isMissingExpanded ? '접기' : `외 ${hiddenMissingCount}개 더 보기`}
            </button>
          )}
        </div>
      )}

      <a className="job-card-link" href={result.originalUrl} target="_blank" rel="noreferrer">
        원문 보기 →
      </a>
    </article>
  )
}

export default JobCard
