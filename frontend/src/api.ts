import type {
  EvaluationResponse,
  JobApplicationResponse,
  ProfileSettings,
  UserProfile,
  UserProfileUpdateRequest,
  Weights,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

interface ErrorBody {
  message?: string
  errors?: { field: string; reason: string }[]
}

// 서버 ErrorResponse(message, errors[])가 있으면 그 내용을 보여주고, 없으면 상태 코드만 표시한다.
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: { field: string; reason: string }[]

  constructor(status: number, body: ErrorBody | null) {
    super(body?.message ? `${body.message} (${status})` : `API 요청 실패 (${status})`)
    this.status = status
    this.fieldErrors = body?.errors ?? []
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorBody | null
    throw new ApiError(response.status, body)
  }
  return response.json() as Promise<T>
}

export async function getProfile(userProfileId: number): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/${userProfileId}`)
  return handleResponse<UserProfile>(response)
}

export async function getProfileSettings(): Promise<ProfileSettings> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/settings`)
  return handleResponse<ProfileSettings>(response)
}

export async function updateProfile(userProfileId: number, request: UserProfileUpdateRequest): Promise<UserProfile> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/${userProfileId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return handleResponse<UserProfile>(response)
}

export async function updateWeights(userProfileId: number, weights: Weights): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/${userProfileId}/weights`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(weights),
  })
  await handleResponse(response)
}

export async function markApplied(userProfileId: number, jobPostingId: number): Promise<JobApplicationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/${userProfileId}/jobs/${jobPostingId}/applied`, {
    method: 'PUT',
  })
  return handleResponse<JobApplicationResponse>(response)
}

export async function unmarkApplied(userProfileId: number, jobPostingId: number): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/users/profile/${userProfileId}/jobs/${jobPostingId}/applied`, {
    method: 'DELETE',
  })
  // 204 No Content라 본문을 파싱하지 않는다.
  if (!response.ok) {
    throw new Error(`API 요청 실패 (${response.status})`)
  }
}

export async function evaluateAll(userProfileId: number): Promise<EvaluationResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/evaluations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userProfileId }),
  })
  return handleResponse<EvaluationResponse[]>(response)
}
