import { useState, type KeyboardEvent } from 'react'

interface TagInputProps {
  tags: string[]
  onChange: (tags: string[]) => void
  placeholder?: string
  disabled?: boolean
  ariaLabel: string
}

// Enter·쉼표로 태그를 추가하고, 입력이 비어 있을 때 Backspace로 마지막 태그를 지운다.
// 서버도 lowercase·중복 제거를 하지만, 화면에서도 대소문자만 다른 중복은 미리 막는다.
function TagInput({ tags, onChange, placeholder, disabled = false, ariaLabel }: TagInputProps) {
  const [draft, setDraft] = useState('')

  function addTags(raw: string) {
    const existing = new Set(tags.map((tag) => tag.toLowerCase()))
    const next = [...tags]
    for (const part of raw.split(',')) {
      const value = part.trim()
      if (value && !existing.has(value.toLowerCase())) {
        existing.add(value.toLowerCase())
        next.push(value)
      }
    }
    if (next.length !== tags.length) onChange(next)
    setDraft('')
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // 한글 조합 중 Enter는 조합 확정용이라 태그로 추가하지 않는다.
    if (event.nativeEvent.isComposing) return
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      addTags(draft)
    } else if (event.key === 'Backspace' && draft === '' && tags.length > 0) {
      onChange(tags.slice(0, -1))
    }
  }

  return (
    <div className={`tag-input ${disabled ? 'tag-input-disabled' : ''}`}>
      {tags.map((tag) => (
        <span key={tag} className="tag">
          {tag}
          {!disabled && (
            <button
              type="button"
              className="tag-remove"
              onClick={() => onChange(tags.filter((t) => t !== tag))}
              aria-label={`${tag} 삭제`}
            >
              ✕
            </button>
          )}
        </span>
      ))}
      {!disabled && (
        <input
          className="tag-input-field"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={() => draft.trim() && addTags(draft)}
          placeholder={tags.length === 0 ? placeholder : ''}
          aria-label={ariaLabel}
        />
      )}
      {disabled && tags.length === 0 && <span className="tag-input-empty">없음</span>}
    </div>
  )
}

export default TagInput
