package com.jinwoo.jobfit.domain.user.service;

import com.jinwoo.jobfit.domain.user.exception.ProfileEditDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공개 배포(prod)에서 데모 프로필이 바뀌거나 지워지지 않도록 프로필 생성·수정·삭제를 환경변수로 막는다.
 * env를 빠뜨려도 열리지 않도록 기본값은 false(차단)이며, 로컬에서는 PROFILE_EDIT_ENABLED=true로 연다.
 * 가중치 수정(PUT .../weights)은 평가 흐름에 쓰이므로 이 정책의 대상이 아니다.
 */
@Component
public class ProfileEditPolicy {

    private final boolean editEnabled;

    public ProfileEditPolicy(@Value("${jobfit.profile.edit-enabled:false}") boolean editEnabled) {
        this.editEnabled = editEnabled;
    }

    public boolean isEditEnabled() {
        return editEnabled;
    }

    public void assertEditable() {
        if (!editEnabled) {
            throw new ProfileEditDisabledException("이 환경에서는 프로필을 생성·수정·삭제할 수 없습니다.");
        }
    }
}
