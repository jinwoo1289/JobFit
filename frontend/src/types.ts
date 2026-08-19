export interface Weights {
  skillWeight: number
  experienceWeight: number
  preferenceWeight: number
  certificateWeight: number
}

export type EvaluationVerdict = 'FIT' | 'UNFIT'

export interface EvaluationReasoning {
  matchedPoints: string[]
  gapPoints: string[]
  checkPoints: string[]
}

export interface EvaluationResponse {
  jobPostingId: number
  companyName: string
  title: string
  originalUrl: string
  totalScore: number
  skillScore: number
  experienceScore: number
  preferenceScore: number
  certificateScore: number
  verdict: EvaluationVerdict
  failedReasons: string[]
  reasoning: EvaluationReasoning
}
