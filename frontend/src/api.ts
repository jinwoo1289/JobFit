import type { EvaluationResponse, JobApplicationResponse, Weights } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(`API 요청 실패 (${response.status})`)
  }
  return response.json() as Promise<T>
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
