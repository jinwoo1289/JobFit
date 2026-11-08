package com.jinwoo.jobfit.global.policy;

import com.jinwoo.jobfit.global.exception.EditDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공개 배포(prod)에서 쓰기 API를 환경변수 하나로 막는다. 여러 도메인이 함께 쓰므로 global에 둔다.
 * - 프로필 생성·수정·삭제: 데모 프로필이 바뀌거나 지워지지 않도록 한다.
 *   가중치 수정(PUT .../weights)은 평가 흐름에 쓰이므로 이 정책의 대상이 아니다.
 * - 공고 등록(POST /api/jobs)과 강제 재추출(POST /api/jobs/{id}/re-extract): 캐시 없이 LLM 호출로 이어져 비용이 나간다.
 * env를 빠뜨려도 열리지 않도록 기본값은 false(차단)이며, 로컬에서는 PROFILE_EDIT_ENABLED=true로 연다.
 * 프로퍼티 키(jobfit.profile.edit-enabled)와 환경변수 이름은 배포 설정과 맞물려 있어 이름이 바뀌어도 그대로 둔다.
 */
@Component
public class EditPolicy {

    private final boolean editEnabled;

    public EditPolicy(@Value("${jobfit.profile.edit-enabled:false}") boolean editEnabled) {
        this.editEnabled = editEnabled;
    }

    public boolean isEditEnabled() {
        return editEnabled;
    }

    public void assertProfileEditable() {
        if (!editEnabled) {
            throw new EditDisabledException("이 환경에서는 프로필을 생성·수정·삭제할 수 없습니다.");
        }
    }

    public void assertJobDataEditable() {
        if (!editEnabled) {
            throw new EditDisabledException("이 환경에서는 공고를 등록하거나 재추출할 수 없습니다.");
        }
    }
}
