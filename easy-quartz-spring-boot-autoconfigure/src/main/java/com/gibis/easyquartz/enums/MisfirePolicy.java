package com.gibis.easyquartz.enums;

/**
 * Misfire 정책 (통합)
 */
public enum MisfirePolicy {
    /**
     * 놓친 실행은 무시하고 다음 예정 시간까지 대기
     */
    DO_NOTHING,

    /**
     * 놓친 실행을 즉시 실행하고 정상 스케줄로 진행
     */
    FIRE_AND_PROCEED,

    /**
     * Misfire를 무시하고 모든 놓친 실행을 수행
     */
    IGNORE_MISFIRES
}
