import { useRef, useState } from 'react'
import { evaluateAll, markApplied, unmarkApplied, updateWeights } from './api'
import JobCard from './components/JobCard'
import JobCardSkeleton from './components/JobCardSkeleton'
import ProfileEditor from './components/ProfileEditor'
import WeightSliders from './components/WeightSliders'
import type { EvaluationResponse, EvaluationVerdict, Weights } from './types'
import './App.css'

// 로컬과 배포(Render) DB의 프로필 id가 달라 환경변수로 주입한다. 잘못된 프로필로 조용히 조회되지 않도록
// 기본값을 두지 않고, 값이 없거나 양의 정수가 아니면 null로 두어 화면에 설정 누락 에러를 표시한다.
const USER_PROFILE_ID = parseUserProfileId(import.meta.env.VITE_USER_PROFILE_ID)
const NOTICE_DISMISSED_KEY = 'jobfit-cold-start-notice-dismissed'
const SKELETON_COUNT = 6
const APPLY_ERROR_DURATION_MS = 3000

const DEFAULT_WEIGHTS: Weights = {
  skillWeight: 40,
  experienceWeight: 30,
  preferenceWeight: 20,
  certificateWeight: 10,
}

// 목록 정렬 순서: FIT → 정보 부족 → UNFIT. 같은 판정 안에서는 종합 점수 내림차순.
const VERDICT_ORDER: Record<EvaluationVerdict, number> = {
  FIT: 0,
  INSUFFICIENT_INFO: 1,
  UNFIT: 2,
}

function sortResults(results: EvaluationResponse[]): EvaluationResponse[] {
  return [...results].sort(
    (a, b) => VERDICT_ORDER[a.verdict] - VERDICT_ORDER[b.verdict] || b.totalScore - a.totalScore,
  )
}

function localIsoNow(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function parseUserProfileId(value: string | undefined): number | null {
  const id = Number(value)
  return value && Number.isInteger(id) && id > 0 ? id : null
}

type View = 'evaluate' | 'profile'

function App() {
  const [view, setView] = useState<View>('evaluate')
  // 마지막 평가 이후 프로필을 저장했는지. 저장과 재평가를 분리했으므로 결과가 이전 프로필 기준임을 알린다.
  const [isProfileChangedSinceEvaluation, setIsProfileChangedSinceEvaluation] = useState(false)
  const [weights, setWeights] = useState<Weights>(DEFAULT_WEIGHTS)
  const [results, setResults] = useState<EvaluationResponse[]>([])
  // 한 번이라도 평가에 성공했는지. 버튼 문구를 "평가" → "재평가"로 바꾸는 데 쓴다.
  const [hasEvaluated, setHasEvaluated] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showAppliedOnly, setShowAppliedOnly] = useState(false)
  // 지원 완료 체크 요청이 진행 중인 공고 id. 응답 전 연타로 요청이 엇갈리지 않도록 그동안 체크박스를 잠근다.
  const [applyPendingIds, setApplyPendingIds] = useState<Set<number>>(() => new Set())
  const [applyErrors, setApplyErrors] = useState<Record<number, string>>({})
  const applyErrorTimers = useRef<Record<number, ReturnType<typeof setTimeout>>>({})
  const [isNoticeDismissed, setIsNoticeDismissed] = useState(
    () => localStorage.getItem(NOTICE_DISMISSED_KEY) === 'true',
  )

  function dismissNotice() {
    localStorage.setItem(NOTICE_DISMISSED_KEY, 'true')
    setIsNoticeDismissed(true)
  }

  const totalWeight =
    weights.skillWeight + weights.experienceWeight + weights.preferenceWeight + weights.certificateWeight
  const isWeightValid = totalWeight === 100

  function handleWeightChange(key: keyof Weights, value: number) {
    setWeights((prev) => ({ ...prev, [key]: value }))
  }

  async function handleEvaluate() {
    // 평가 중 중복 클릭 방지 (버튼 disabled와 별개로, 상태 반영 전 연타도 막는다)
    if (USER_PROFILE_ID === null || isLoading) return
    setIsLoading(true)
    setError(null)
    try {
      await updateWeights(USER_PROFILE_ID, weights)
      const evaluations = await evaluateAll(USER_PROFILE_ID)
      setResults(evaluations)
      setHasEvaluated(true)
      setIsProfileChangedSinceEvaluation(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : '평가 요청 중 오류가 발생했습니다.')
    } finally {
      setIsLoading(false)
    }
  }

  function updateApplied(jobPostingId: number, applied: boolean, appliedAt: string | null) {
    setResults((prev) =>
      prev.map((result) => (result.jobPostingId === jobPostingId ? { ...result, applied, appliedAt } : result)),
    )
  }

  function setApplyPending(jobPostingId: number, pending: boolean) {
    setApplyPendingIds((prev) => {
      const next = new Set(prev)
      if (pending) next.add(jobPostingId)
      else next.delete(jobPostingId)
      return next
    })
  }

  function showApplyError(jobPostingId: number, message: string) {
    clearTimeout(applyErrorTimers.current[jobPostingId])
    setApplyErrors((prev) => ({ ...prev, [jobPostingId]: message }))
    applyErrorTimers.current[jobPostingId] = setTimeout(() => {
      setApplyErrors((prev) => {
        const { [jobPostingId]: _removed, ...rest } = prev
        return rest
      })
    }, APPLY_ERROR_DURATION_MS)
  }

  // 체크 상태를 먼저 바꾸고(낙관적 갱신), 서버 요청이 실패하면 이전 상태로 되돌린다.
  async function handleToggleApplied(result: EvaluationResponse, nextApplied: boolean) {
    if (USER_PROFILE_ID === null || applyPendingIds.has(result.jobPostingId)) return
    const { jobPostingId, applied: prevApplied, appliedAt: prevAppliedAt } = result
    setApplyPending(jobPostingId, true)
    // 응답 전 임시 표시용 시각. toISOString()은 UTC라 자정 무렵 날짜가 하루 어긋나므로 로컬 시각으로 만든다.
    updateApplied(jobPostingId, nextApplied, nextApplied ? (prevAppliedAt ?? localIsoNow()) : null)
    try {
      if (nextApplied) {
        const saved = await markApplied(USER_PROFILE_ID, jobPostingId)
        updateApplied(jobPostingId, true, saved.appliedAt)
      } else {
        await unmarkApplied(USER_PROFILE_ID, jobPostingId)
      }
    } catch {
      updateApplied(jobPostingId, prevApplied, prevAppliedAt)
      showApplyError(jobPostingId, nextApplied ? '지원 완료 저장에 실패했습니다.' : '지원 완료 해제에 실패했습니다.')
    } finally {
      setApplyPending(jobPostingId, false)
    }
  }

  // 마감 여부는 failedReasons 문자열이 아니라 응답의 expired 플래그로 판단한다.
  // 지원 여부는 정렬 기준에 넣지 않는다(체크해도 카드 순서가 바뀌지 않아야 함).
  const activeResults = sortResults(results.filter((result) => !result.expired))
  const expiredResults = sortResults(results.filter((result) => result.expired))
  // 순위는 필터와 무관하게 전체 목록 기준으로 매겨, "지원한 공고만 보기"를 켜도 번호가 바뀌지 않게 한다.
  const fitRanks = new Map<number, number>()
  activeResults
    .filter((result) => result.verdict === 'FIT')
    .forEach((result, i) => fitRanks.set(result.jobPostingId, i + 1))
  const isVisible = (result: EvaluationResponse) => !showAppliedOnly || result.applied
  const visibleActiveResults = activeResults.filter(isVisible)
  const visibleExpiredResults = expiredResults.filter(isVisible)

  function renderCard(result: EvaluationResponse, rank?: number) {
    return (
      <JobCard
        key={result.jobPostingId}
        result={result}
        rank={rank}
        onToggleApplied={(nextApplied) => handleToggleApplied(result, nextApplied)}
        isApplyPending={applyPendingIds.has(result.jobPostingId)}
        applyError={applyErrors[result.jobPostingId]}
      />
    )
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>JobFit</h1>
        <p>
          {view === 'evaluate'
            ? '가중치를 조정하고 재평가하여 추천 공고를 확인하세요.'
            : '평가에 쓰이는 프로필을 확인하고 수정하세요.'}
        </p>
      </header>

      <nav className="view-tabs" aria-label="화면 전환">
        <button
          className={`view-tab ${view === 'evaluate' ? 'view-tab-active' : ''}`}
          onClick={() => setView('evaluate')}
          aria-current={view === 'evaluate' ? 'page' : undefined}
        >
          평가
        </button>
        <button
          className={`view-tab ${view === 'profile' ? 'view-tab-active' : ''}`}
          onClick={() => setView('profile')}
          aria-current={view === 'profile' ? 'page' : undefined}
        >
          프로필
        </button>
      </nav>

      {!isNoticeDismissed && (
        <div className="cold-start-notice">
          <span>
            ⓘ 서버가 Render 무료 플랜에서 실행 중이라, 일정 시간 미사용 시 첫 요청이 최대 1분 정도 걸릴 수 있습니다.
          </span>
          <button className="cold-start-notice-close" onClick={dismissNotice} aria-label="안내 닫기">
            ✕
          </button>
        </div>
      )}

      {USER_PROFILE_ID === null && (
        <div className="config-error" role="alert">
          설정 오류: VITE_USER_PROFILE_ID 환경변수가 없거나 올바르지 않습니다. 빌드 환경에 프로필 id를 설정한 뒤 다시
          배포하세요.
        </div>
      )}

      {view === 'profile' && USER_PROFILE_ID !== null && (
        <ProfileEditor userProfileId={USER_PROFILE_ID} onSaved={() => setIsProfileChangedSinceEvaluation(true)} />
      )}

      {view === 'evaluate' && (
        <>
        {isProfileChangedSinceEvaluation && hasEvaluated && (
          <div className="profile-saved-notice" role="status">
            프로필이 변경되어 아래 결과는 이전 프로필 기준입니다. 재평가하면 반영되며, 근거 생성 LLM 호출이 공고 수만큼
            발생할 수 있습니다.
          </div>
        )}

        <WeightSliders weights={weights} onChange={handleWeightChange} />

        <div className="evaluate-bar">
          <button className="evaluate-button" onClick={handleEvaluate} disabled={USER_PROFILE_ID === null || !isWeightValid || isLoading}>
            {isLoading ? (
              <>
                <span className="button-spinner" />
                평가 중...
              </>
            ) : hasEvaluated ? (
              '재평가'
            ) : (
              '평가'
            )}
          </button>
          {!isWeightValid && <span className="evaluate-warning">가중치 합계가 100이 되어야 합니다.</span>}
          {error && <span className="evaluate-error">{error}</span>}
        </div>

        <section className="results">
          {results.length === 0 && !isLoading && (
            <p className="results-empty">{hasEvaluated ? '평가할 공고가 없습니다.' : '평가 버튼을 눌러 추천 공고를 확인하세요.'}</p>
          )}
          {results.length > 0 && !isLoading && (
            <div className="results-toolbar">
              <label className="applied-filter">
                <input
                  type="checkbox"
                  checked={showAppliedOnly}
                  onChange={(event) => setShowAppliedOnly(event.target.checked)}
                />
                지원한 공고만 보기
              </label>
            </div>
          )}
          {showAppliedOnly && !isLoading && results.length > 0 &&
            visibleActiveResults.length === 0 && visibleExpiredResults.length === 0 && (
              <p className="results-empty">지원 완료로 체크한 공고가 없습니다.</p>
            )}
          <div className="results-grid">
            {isLoading
              ? Array.from({ length: SKELETON_COUNT }).map((_, i) => <JobCardSkeleton key={i} />)
              : visibleActiveResults.map((result) => renderCard(result, fitRanks.get(result.jobPostingId)))}
          </div>

          {!isLoading && visibleExpiredResults.length > 0 && (
            <details className="expired-section">
              <summary className="expired-section-summary">마감된 공고 {visibleExpiredResults.length}개</summary>
              <div className="results-grid">{visibleExpiredResults.map((result) => renderCard(result))}</div>
            </details>
          )}
        </section>
        </>
      )}
    </div>
  )
}

export default App
