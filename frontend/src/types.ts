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

export interface MatchedSkill {
  skill: string
  // 이 기술을 사용한 지원자 프로젝트명. 없으면 보유 기술 목록에만 있는 경우.
  projectNames: string[]
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
  // 판정과 별개로 보여줄 안내 문구 (예: IT 통합 채용 공고의 직무 범위 확인 안내)
  notices: string[]
  reasoning: EvaluationReasoning
  // 마감일이 지난 공고 여부. failedReasons 문자열 대신 이 값으로 마감 공고를 분리한다.
  expired: boolean
  // 기술 점수 기준: 필수 기술 requiredSkillCount개 중 matchedSkillCount개 보유
  requiredSkillCount: number
  matchedSkillCount: number
  // 경험 점수 기준: 필수 기술(중복 제거) experienceSkillCount개 중, 가장 많이 겹치는 프로젝트 하나에서 사용한 개수
  experienceSkillCount: number
  experienceMatchedCount: number
  matchedSkills: MatchedSkill[]
  missingSkills: string[]
  // 지원 완료 체크 여부와 체크 시각(서버 로컬 시각 ISO 문자열, 미체크면 null)
  applied: boolean
  appliedAt: string | null
}

export interface JobApplicationResponse {
  jobPostingId: number
  appliedAt: string
}
