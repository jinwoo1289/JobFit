export interface Weights {
  skillWeight: number
  experienceWeight: number
  preferenceWeight: number
  certificateWeight: number
}

export type EvaluationVerdict = 'FIT' | 'UNFIT' | 'INSUFFICIENT_INFO'

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
  skillExcluded: boolean
  experienceScore: number
  experienceExcluded: boolean
  preferenceScore: number
  preferenceExcluded: boolean
  certificateScore: number
  certificateExcluded: boolean
  verdict: EvaluationVerdict
  failedReasons: string[]
  reasoning: EvaluationReasoning
}
