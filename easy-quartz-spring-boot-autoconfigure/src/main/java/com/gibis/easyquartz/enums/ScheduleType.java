package com.gibis.easyquartz.enums;

/**
 * 스케줄 타입
 */
public enum ScheduleType {
    /**
     * CRON 표현식 기반 스케줄링
     * 예: "0 0 * * * ?" (매시 정각)
     */
    CRON,

    /**
     * 고정 간격 (이전 실행 시작 시점 기준)
     * Spring의 @Scheduled(fixedRate) 와 동일
     */
    FIXED_RATE,

    /**
     * 고정 지연 (이전 실행 종료 시점 기준)
     * Spring의 @Scheduled(fixedDelay) 와 동일
     */
    FIXED_DELAY,

    /**
     * 달력 기반 간격 (일/주/월 단위)
     * DST(일광절약시간) 처리 포함
     */
    CALENDAR,

    /**
     * 매일 특정 시간대에 반복 실행
     * 예: 평일 9-18시, 30분마다
     */
    DAILY_TIME
}
