import { useEffect, useState } from 'react'
import { ApiError, getProfile, getProfileSettings, updateProfile } from '../api'
import type { CareerLevel, EmploymentType, UserProfile, UserProfileUpdateRequest } from '../types'
import TagInput from './TagInput'

// 서버 JobCategory 기준 프리셋. 공고의 IT 통합 채용 판정이 희망 직무 문자열의 키워드("개발자"·"엔지니어" 등)로
// 개발 계열 여부를 보므로, 프리셋은 모두 그 키워드를 포함하게 둔다. 그 밖의 값은 직접 입력한다.
const JOB_PRESETS = [
  '백엔드 개발자',
  '프론트엔드 개발자',
  '풀스택 개발자',
  '모바일 개발자',
  '데이터 엔지니어',
  'AI/ML 엔지니어',
  'DevOps 엔지니어',
  'QA 엔지니어',
]
const CUSTOM_JOB = '__custom__'

const CAREER_LEVEL_LABELS: Record<CareerLevel, string> = {
  NEW_GRADUATE: '신입',
  EXPERIENCED: '경력',
}

const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: '정규직',
  CONTRACT: '계약직',
  INTERN: '인턴',
  FREELANCE: '프리랜서',
}

const EMPLOYMENT_TYPES = Object.keys(EMPLOYMENT_TYPE_LABELS) as EmploymentType[]

interface ProjectRow {
  key: number
  projectName: string
  description: string
  techStack: string[]
}

interface CertificateRow {
  key: number
  certificateName: string
  grade: string
  acquiredAt: string
}

interface ProfileForm {
  desiredJob: string
  careerLevel: CareerLevel
  yearsOfExperience: string
  desiredLocation: string
  employmentTypes: EmploymentType[]
  skills: string[]
  projects: ProjectRow[]
  certificates: CertificateRow[]
}

let nextRowKey = 1

function toForm(profile: UserProfile): ProfileForm {
  return {
    desiredJob: profile.desiredJob,
    careerLevel: profile.careerLevel,
    yearsOfExperience: profile.yearsOfExperience === null ? '' : String(profile.yearsOfExperience),
    desiredLocation: profile.desiredLocation ?? '',
    employmentTypes: profile.employmentTypes,
    skills: profile.skills.map((skill) => skill.skillName),
    projects: profile.projects.map((project) => ({
      key: nextRowKey++,
      projectName: project.projectName,
      description: project.description ?? '',
      techStack: project.techStack,
    })),
    certificates: profile.certificates.map((certificate) => ({
      key: nextRowKey++,
      certificateName: certificate.certificateName,
      grade: certificate.grade ?? '',
      acquiredAt: certificate.acquiredAt ?? '',
    })),
  }
}

function toRequest(form: ProfileForm): UserProfileUpdateRequest {
  const blankToNull = (value: string) => (value.trim() === '' ? null : value.trim())
  return {
    desiredJob: form.desiredJob.trim(),
    careerLevel: form.careerLevel,
    yearsOfExperience: form.yearsOfExperience === '' ? null : Number(form.yearsOfExperience),
    desiredLocation: blankToNull(form.desiredLocation),
    // 선택 순서와 무관하게 같은 값이면 같은 요청이 되도록(변경 감지용) 선언 순서로 정렬한다.
    employmentTypes: EMPLOYMENT_TYPES.filter((type) => form.employmentTypes.includes(type)),
    skills: form.skills,
    projects: form.projects.map((project) => ({
      projectName: project.projectName.trim(),
      description: blankToNull(project.description),
      techStack: project.techStack,
    })),
    certificates: form.certificates.map((certificate) => ({
      certificateName: certificate.certificateName.trim(),
      grade: blankToNull(certificate.grade),
      acquiredAt: blankToNull(certificate.acquiredAt),
    })),
  }
}

function validate(form: ProfileForm): string[] {
  const problems: string[] = []
  if (form.desiredJob.trim() === '') problems.push('희망 직무를 입력하세요.')
  if (form.yearsOfExperience !== '') {
    const years = Number(form.yearsOfExperience)
    if (!Number.isInteger(years) || years < 0) problems.push('경력 연차는 0 이상의 정수여야 합니다.')
  }
  if (form.skills.length === 0) problems.push('보유 기술을 1개 이상 입력하세요.')
  if (form.projects.some((project) => project.projectName.trim() === '')) problems.push('프로젝트 이름을 입력하세요.')
  if (form.certificates.some((certificate) => certificate.certificateName.trim() === '')) {
    problems.push('자격증·어학 이름을 입력하세요.')
  }
  return problems
}

interface ProfileEditorProps {
  userProfileId: number
  onSaved: () => void
}

function ProfileEditor({ userProfileId, onSaved }: ProfileEditorProps) {
  const [form, setForm] = useState<ProfileForm | null>(null)
  // 마지막으로 불러온(저장된) 값. 변경이 없으면 저장 버튼을 비활성화하는 데 쓴다.
  const [savedSnapshot, setSavedSnapshot] = useState('')
  const [editEnabled, setEditEnabled] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [isSaved, setIsSaved] = useState(false)
  const [isCustomJob, setIsCustomJob] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.all([getProfile(userProfileId), getProfileSettings()])
      .then(([profile, settings]) => {
        if (cancelled) return
        const loaded = toForm(profile)
        setSavedSnapshot(JSON.stringify(toRequest(loaded)))
        setForm(loaded)
        setIsCustomJob(!JOB_PRESETS.includes(profile.desiredJob))
        setEditEnabled(settings.editEnabled)
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : '프로필을 불러오지 못했습니다.')
      })
    return () => {
      cancelled = true
    }
  }, [userProfileId])

  if (loadError) {
    return <div className="config-error" role="alert">{loadError}</div>
  }
  if (form === null) {
    return <p className="results-empty">프로필을 불러오는 중...</p>
  }

  const problems = validate(form)
  const isDirty = JSON.stringify(toRequest(form)) !== savedSnapshot
  const readOnly = !editEnabled

  function update(patch: Partial<ProfileForm>) {
    setForm((prev) => (prev === null ? prev : { ...prev, ...patch }))
    setIsSaved(false)
  }

  function updateProject(key: number, patch: Partial<ProjectRow>) {
    update({ projects: form!.projects.map((project) => (project.key === key ? { ...project, ...patch } : project)) })
  }

  function updateCertificate(key: number, patch: Partial<CertificateRow>) {
    update({
      certificates: form!.certificates.map((certificate) =>
        certificate.key === key ? { ...certificate, ...patch } : certificate,
      ),
    })
  }

  async function handleSave() {
    if (form === null || isSaving || problems.length > 0) return
    setIsSaving(true)
    setSaveError(null)
    try {
      const saved = await updateProfile(userProfileId, toRequest(form))
      const savedForm = toForm(saved)
      setSavedSnapshot(JSON.stringify(toRequest(savedForm)))
      setForm(savedForm)
      setIsSaved(true)
      onSaved()
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) setEditEnabled(false)
      const fieldDetail =
        err instanceof ApiError && err.fieldErrors.length > 0
          ? ' — ' + err.fieldErrors.map((e) => `${e.field}: ${e.reason}`).join(', ')
          : ''
      setSaveError((err instanceof Error ? err.message : '저장 중 오류가 발생했습니다.') + fieldDetail)
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="profile-editor">
      {readOnly && (
        <div className="profile-readonly-notice" role="status">
          이 환경에서는 프로필을 수정할 수 없습니다. 읽기 전용으로 표시합니다.
        </div>
      )}

      {/* disabled fieldset이 하위 입력을 한꺼번에 막아 읽기 전용 화면이 된다. */}
      <fieldset className="profile-fieldset" disabled={readOnly || isSaving}>
        <section className="profile-section">
          <h2>기본 정보</h2>
          <div className="profile-grid">
            <label className="profile-field">
              <span>희망 직무</span>
              <select
                value={isCustomJob ? CUSTOM_JOB : form.desiredJob}
                onChange={(event) => {
                  if (event.target.value === CUSTOM_JOB) {
                    setIsCustomJob(true)
                  } else {
                    setIsCustomJob(false)
                    update({ desiredJob: event.target.value })
                  }
                }}
              >
                {JOB_PRESETS.map((job) => (
                  <option key={job} value={job}>{job}</option>
                ))}
                <option value={CUSTOM_JOB}>직접 입력</option>
              </select>
              {isCustomJob && (
                <input
                  value={form.desiredJob}
                  onChange={(event) => update({ desiredJob: event.target.value })}
                  placeholder="예: 서버 개발자"
                  aria-label="희망 직무 직접 입력"
                />
              )}
            </label>
            <div className="profile-field" role="group" aria-labelledby="employment-types-label">
              <span id="employment-types-label">고용형태</span>
              <div className="checkbox-group">
                {EMPLOYMENT_TYPES.map((type) => (
                  <label key={type} className="checkbox-option">
                    <input
                      type="checkbox"
                      checked={form.employmentTypes.includes(type)}
                      onChange={(event) =>
                        update({
                          employmentTypes: event.target.checked
                            ? [...form.employmentTypes, type]
                            : form.employmentTypes.filter((t) => t !== type),
                        })
                      }
                    />
                    {EMPLOYMENT_TYPE_LABELS[type]}
                  </label>
                ))}
              </div>
              <span className="profile-field-note">
                {form.employmentTypes.length === 0 ? '선택 안 함 = 무관 (모든 공고 통과)' : '선택한 고용형태의 공고만 통과'}
              </span>
            </div>
            <label className="profile-field">
              <span>경력 구분</span>
              <select
                value={form.careerLevel}
                onChange={(event) => update({ careerLevel: event.target.value as CareerLevel })}
              >
                {(Object.keys(CAREER_LEVEL_LABELS) as CareerLevel[]).map((level) => (
                  <option key={level} value={level}>{CAREER_LEVEL_LABELS[level]}</option>
                ))}
              </select>
            </label>
            <label className="profile-field">
              <span>경력 연차 (년)</span>
              <input
                type="number"
                min={0}
                step={1}
                value={form.yearsOfExperience}
                onChange={(event) => update({ yearsOfExperience: event.target.value })}
                placeholder="0"
              />
            </label>
          </div>
        </section>

        <section className="profile-section">
          <h2>보유 기술</h2>
          {!readOnly && (
            <p className="profile-hint">Enter 또는 쉼표로 추가합니다. 저장 시 소문자로 통일되고 중복은 제거됩니다.</p>
          )}
          <TagInput
            tags={form.skills}
            onChange={(skills) => update({ skills })}
            placeholder="예: Java, Spring Boot, PostgreSQL"
            disabled={readOnly || isSaving}
            ariaLabel="보유 기술 입력"
          />
        </section>

        <section className="profile-section">
          <h2>자격증·어학</h2>
          {form.certificates.length === 0 && <p className="profile-hint">등록된 자격증·어학이 없습니다.</p>}
          {form.certificates.map((certificate) => (
            <div key={certificate.key} className="profile-row">
              <input
                value={certificate.certificateName}
                onChange={(event) => updateCertificate(certificate.key, { certificateName: event.target.value })}
                placeholder="이름 (예: 정보처리기사, TOEIC)"
                aria-label="자격증·어학 이름"
              />
              <input
                value={certificate.grade}
                onChange={(event) => updateCertificate(certificate.key, { grade: event.target.value })}
                placeholder="등급·점수 (선택)"
                aria-label="등급 또는 점수"
              />
              <input
                type="date"
                value={certificate.acquiredAt}
                onChange={(event) => updateCertificate(certificate.key, { acquiredAt: event.target.value })}
                aria-label="취득일"
              />
              {!readOnly && (
                <button
                  type="button"
                  className="profile-row-remove"
                  onClick={() => update({ certificates: form.certificates.filter((c) => c.key !== certificate.key) })}
                >
                  삭제
                </button>
              )}
            </div>
          ))}
          {!readOnly && (
            <button
              type="button"
              className="profile-add-button"
              onClick={() =>
                update({
                  certificates: [
                    ...form.certificates,
                    { key: nextRowKey++, certificateName: '', grade: '', acquiredAt: '' },
                  ],
                })
              }
            >
              + 자격증·어학 추가
            </button>
          )}
        </section>

        <section className="profile-section">
          <h2>프로젝트</h2>
          {form.projects.length === 0 && <p className="profile-hint">등록된 프로젝트가 없습니다.</p>}
          {form.projects.map((project) => (
            <div key={project.key} className="profile-project">
              <div className="profile-project-header">
                <input
                  value={project.projectName}
                  onChange={(event) => updateProject(project.key, { projectName: event.target.value })}
                  placeholder="프로젝트 이름"
                  aria-label="프로젝트 이름"
                />
                {!readOnly && (
                  <button
                    type="button"
                    className="profile-row-remove"
                    onClick={() => update({ projects: form.projects.filter((p) => p.key !== project.key) })}
                  >
                    삭제
                  </button>
                )}
              </div>
              <textarea
                value={project.description}
                onChange={(event) => updateProject(project.key, { description: event.target.value })}
                placeholder="설명 (맡은 역할, 해결한 문제 등)"
                rows={3}
                aria-label="프로젝트 설명"
              />
              <TagInput
                tags={project.techStack}
                onChange={(techStack) => updateProject(project.key, { techStack })}
                placeholder="사용 기술 (Enter로 추가)"
                disabled={readOnly || isSaving}
                ariaLabel="프로젝트 사용 기술 입력"
              />
            </div>
          ))}
          {!readOnly && (
            <button
              type="button"
              className="profile-add-button"
              onClick={() =>
                update({
                  projects: [...form.projects, { key: nextRowKey++, projectName: '', description: '', techStack: [] }],
                })
              }
            >
              + 프로젝트 추가
            </button>
          )}
        </section>

        <section className="profile-section">
          <h2>선호 조건</h2>
          <div className="profile-grid">
            <label className="profile-field">
              <span>희망 근무지</span>
              <input
                value={form.desiredLocation}
                onChange={(event) => update({ desiredLocation: event.target.value })}
                placeholder="예: 서울, 판교"
              />
            </label>
          </div>
          <p className="profile-hint">평가 가중치는 평가 탭에서 조정합니다.</p>
        </section>
      </fieldset>

      {!readOnly && (
        <div className="evaluate-bar">
          <button
            className="evaluate-button"
            onClick={handleSave}
            disabled={isSaving || !isDirty || problems.length > 0}
          >
            {isSaving ? (
              <>
                <span className="button-spinner" />
                저장 중...
              </>
            ) : (
              '저장'
            )}
          </button>
          {isDirty && problems.length > 0 && <span className="evaluate-warning">{problems[0]}</span>}
          {saveError && <span className="evaluate-error">{saveError}</span>}
        </div>
      )}

      {isSaved && (
        <div className="profile-saved-notice" role="status">
          프로필을 저장했습니다. 평가 결과에는 아직 반영되지 않았으며, 평가 탭에서 재평가하면 반영됩니다.
          <br />
          재평가 시 바뀐 프로필로 근거를 다시 만들기 때문에 근거 생성 LLM 호출이 공고 수만큼 발생할 수 있습니다.
        </div>
      )}
    </div>
  )
}

export default ProfileEditor
