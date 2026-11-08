package com.jinwoo.jobfit.global.policy;

import com.jinwoo.jobfit.global.exception.EditDisabledException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공개 배포에서 env를 빠뜨려도 쓰기 API(프로필 편집·공고 등록·재추출)가 열리지 않도록 기본값이 차단(false)인지 고정한다.
 * 로컬 .env(PROFILE_EDIT_ENABLED=true)의 영향을 받지 않도록 Spring Boot 전체 컨텍스트 대신 설정 파일과 빈만 따로 확인한다.
 */
class EditPolicyTest {

    @Test
    void applicationProperties_defaultsToDisabledWhenEnvMissing() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        String declared = properties.getProperty("jobfit.profile.edit-enabled");

        // PROFILE_EDIT_ENABLED가 없을 때 치환되는 값
        String resolved = new PropertyPlaceholderHelper("${", "}", ":", null, true)
                .replacePlaceholders(declared, name -> null);

        assertThat(resolved).isEqualTo("false");
    }

    @Test
    void bean_withoutProperty_isDisabled() {
        new ApplicationContextRunner()
                .withBean(EditPolicy.class)
                .run(context -> {
                    EditPolicy policy = context.getBean(EditPolicy.class);
                    assertThat(policy.isEditEnabled()).isFalse();
                    assertThatThrownBy(policy::assertProfileEditable).isInstanceOf(EditDisabledException.class);
                });
    }

    @Test
    void bean_withPropertyTrue_isEnabled() {
        new ApplicationContextRunner()
                .withBean(EditPolicy.class)
                .withPropertyValues("jobfit.profile.edit-enabled=true")
                .run(context -> assertThat(context.getBean(EditPolicy.class).isEditEnabled()).isTrue());
    }
}
