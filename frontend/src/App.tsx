import { useState } from 'react'
import { evaluateAll, updateWeights } from './api'
import JobCard from './components/JobCard'
import JobCardSkeleton from './components/JobCardSkeleton'
import WeightSliders from './components/WeightSliders'
import type { EvaluationResponse, Weights } from './types'
import './App.css'

const USER_PROFILE_ID = 1
const NOTICE_DISMISSED_KEY = 'jobfit-cold-start-notice-dismissed'
const SKELETON_COUNT = 6

const DEFAULT_WEIGHTS: Weights = {
  skillWeight: 25,
  experienceWeight: 25,
  preferenceWeight: 25,
  certificateWeight: 25,
}

function App() {
  const [weights, setWeights] = useState<Weights>(DEFAULT_WEIGHTS)
  const [results, setResults] = useState<EvaluationResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
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
    setIsLoading(true)
    setError(null)
    try {
      await updateWeights(USER_PROFILE_ID, weights)
      const evaluations = await evaluateAll(USER_PROFILE_ID)
      const sorted = [...evaluations].sort((a, b) => b.totalScore - a.totalScore)
      setResults(sorted)
    } catch (err) {
      setError(err instanceof Error ? err.message : '평가 요청 중 오류가 발생했습니다.')
    } finally {
      setIsLoading(false)
    }
  }

  let fitRank = 0

  return (
    <div className="app">
      <header className="app-header">
        <h1>JobFit</h1>
        <p>가중치를 조정하고 재평가하여 추천 공고를 확인하세요.</p>
      </header>

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

      <WeightSliders weights={weights} onChange={handleWeightChange} />

      <div className="evaluate-bar">
        <button className="evaluate-button" onClick={handleEvaluate} disabled={!isWeightValid || isLoading}>
          {isLoading ? (
            <>
              <span className="button-spinner" />
              평가 중...
            </>
          ) : (
            '재평가'
          )}
        </button>
        {!isWeightValid && <span className="evaluate-warning">가중치 합계가 100이 되어야 합니다.</span>}
        {error && <span className="evaluate-error">{error}</span>}
      </div>

      <section className="results">
        {results.length === 0 && !isLoading && <p className="results-empty">재평가 버튼을 눌러 추천 공고를 확인하세요.</p>}
        <div className="results-grid">
          {isLoading
            ? Array.from({ length: SKELETON_COUNT }).map((_, i) => <JobCardSkeleton key={i} />)
            : results.map((result) => {
                if (result.verdict === 'FIT') fitRank += 1
                return (
                  <JobCard
                    key={result.jobPostingId}
                    result={result}
                    rank={result.verdict === 'FIT' ? fitRank : undefined}
                  />
                )
              })}
        </div>
      </section>
    </div>
  )
}

export default App
