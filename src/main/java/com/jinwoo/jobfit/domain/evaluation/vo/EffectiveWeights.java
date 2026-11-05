package com.jinwoo.jobfit.domain.evaluation.vo;

// 평가 제외 재배분과 필수 요건이 적은 공고의 가중치 이동을 모두 반영한, 총점 계산에 실제로 쓰이는 항목별 가중치.
// 평가 대상 항목이 하나라도 있으면 합계는 100이고, 모두 제외되면 모두 0이다.
public record EffectiveWeights(double skill, double experience, double preference, double certificate) {

    public static EffectiveWeights none() {
        return new EffectiveWeights(0, 0, 0, 0);
    }

    public double sum() {
        return skill + experience + preference + certificate;
    }
}
