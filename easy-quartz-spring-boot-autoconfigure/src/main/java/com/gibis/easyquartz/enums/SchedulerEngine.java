package com.gibis.easyquartz.enums;

/**
 * 스케줄링 실행 엔진을 선택합니다.
 *
 * <p>
 * QUARTZ는 Quartz Scheduler를 사용하여 misfire 정책, 클러스터링, JDBC JobStore 영속화,
 * 캘린더 기반 반복(CALENDAR / DAILY_TIME) 등 Quartz의 모든 기능을 활용합니다.
 * </p>
 *
 * <p>
 * SPRING은 Spring의 {@code TaskScheduler}로 단순 스케줄링을 수행합니다.
 * 외부 의존성과 상태 저장 없이 가볍게 동작하지만 misfire 정책, 영속화, 클러스터링은 지원하지 않으며,
 * CRON / FIXED_RATE / FIXED_DELAY 세 가지 타입에 한정됩니다.
 * </p>
 */
public enum SchedulerEngine {
    QUARTZ,
    SPRING
}
