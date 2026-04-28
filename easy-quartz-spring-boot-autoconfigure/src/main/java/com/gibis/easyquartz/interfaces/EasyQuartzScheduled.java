package com.gibis.easyquartz.interfaces;


import com.gibis.easyquartz.enums.CalendarUnit;
import com.gibis.easyquartz.enums.Day;
import com.gibis.easyquartz.enums.MisfirePolicy;
import com.gibis.easyquartz.enums.ScheduleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 통합 스케줄링 어노테이션 - 모든 스케줄 타입을 하나로 처리
 *
 * 사용 예시:
 *
 * // CRON
 * @EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 * * * ?")
 * public void hourlyTask() { }
 *
 * // FIXED_RATE (3시간마다)
 * @EasyQuartzScheduled(type = ScheduleType.FIXED_RATE, fixedRateHours = 3)
 * public void everyThreeHours() { }
 *
 * // FIXED_DELAY (5분 후 재실행)
 * @EasyQuartzScheduled(type = ScheduleType.FIXED_DELAY, fixedDelayMinutes = 5)
 * public void delayedTask() { }
 *
 * // CALENDAR (매주)
 * @EasyQuartzScheduled(type = ScheduleType.CALENDAR, calendarUnit = CalendarUnit.WEEKS, calendarInterval = 1)
 * public void weeklyTask() { }
 *
 * // DAILY_TIME (평일 9-18시, 30분마다)
 * @EasyQuartzScheduled(
 *     type = ScheduleType.DAILY_TIME,
 *     dailyStartHour = 9, dailyEndHour = 18,
 *     intervalMinutes = 30,
 *     daysOfWeek = {Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI}
 * )
 * public void workHoursTask() { }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyQuartzScheduled {
    // ========================================
    // 기본 설정
    // ========================================

    /**
     * 스케줄 이름 (비우면 "빈이름.메서드이름" 자동 생성)
     */
    String name() default "";

    /**
     * 그룹명 (논리적 분류용)
     */
    String group() default "DEFAULT";

    /**
     * 스케줄 설명
     */
    String description() default "";

    /**
     * 동시 실행 방지 여부
     * true: 이전 실행이 끝나야 다음 실행 시작
     */
    boolean disallowConcurrent() default true;

    // ========================================
    // 스케줄 타입 선택 (필수)
    // ========================================

    /**
     * 스케줄 타입 (필수 선택)
     * - CRON: cron 표현식 사용
     * - FIXED_RATE: 고정 간격 (이전 실행 시작 시점 기준)
     * - FIXED_DELAY: 고정 지연 (이전 실행 종료 시점 기준)
     * - CALENDAR: 달력 단위 (일/주/월)
     * - DAILY_TIME: 매일 특정 시간대 반복
     */
    ScheduleType type();

    // ========================================
    // CRON 전용 옵션
    // ========================================

    /**
     * CRON 표현식 (type=CRON일 때 필수)
     * 예: "0 0 * * * ?" (매시 정각)
     */
    String cron() default "";

    // ========================================
    // FIXED_RATE / FIXED_DELAY 전용 옵션
    // ========================================

    /**
     * 고정 지연 시간 (FIXED_DELAY 전용)
     * 이전 실행이 끝나고 N시간 후 재실행
     */
    long fixedDelayHours() default 0;
    long fixedDelayMinutes() default 0;
    long fixedDelaySeconds() default 0;

    /**
     * 고정 간격 시간 (FIXED_RATE 전용)
     * 이전 실행 시작 후 N시간마다 실행
     */
    long fixedRateHours() default 0;
    long fixedRateMinutes() default 0;
    long fixedRateSeconds() default 0;

    /**
     * 무한 반복 여부 (FIXED 타입 전용)
     */
    boolean repeatForever() default true;

    /**
     * 반복 횟수 (repeatForever=false일 때)
     * 예: repeatCount=5 → 총 6회 실행 (최초 1회 + 반복 5회)
     */
    int repeatCount() default -1;

    // ========================================
    // CALENDAR 전용 옵션
    // ========================================

    /**
     * 달력 단위 (type=CALENDAR일 때 필수)
     */
    CalendarUnit calendarUnit() default CalendarUnit.NONE;

    /**
     * 간격 (calendarInterval=2 &amp;&amp; WEEKS → 2주마다)
     */
    int calendarInterval() default 1;

    /**
     * DST 시간 변경 시 시간대 유지 여부
     */
    boolean preserveHourAcrossDst() default true;

    /**
     * DST로 인해 존재하지 않는 시간이면 건너뛸지 여부
     */
    boolean skipDayIfHourDoesNotExist() default true;

    // ========================================
    // DAILY_TIME 전용 옵션
    // ========================================

    /**
     * 매일 실행 시작 시각 (type=DAILY_TIME일 때 필수)
     */
    int dailyStartHour() default -1;
    int dailyStartMin() default 0;

    /**
     * 매일 실행 종료 시각 (type=DAILY_TIME일 때 필수)
     */
    int dailyEndHour() default -1;
    int dailyEndMin() default 0;

    /**
     * 실행 간격 (최소 1개 필수)
     */
    int intervalHours() default 0;
    int intervalMinutes() default 0;
    int intervalSeconds() default 0;

    /**
     * 실행 요일 (비우면 매일)
     * 예: {Day.MON, Day.WED, Day.FRI}
     */
    Day[] daysOfWeek() default {};

    // ========================================
    // 공통: 시작/종료 시간 제어
    // ========================================

    /**
     * 시작 지연 시간 (초)
     * 예: 60 → 애플리케이션 시작 후 60초 뒤 첫 실행
     */
    long startDelaySeconds() default 0;

    /**
     * 종료 시간 (초)
     * 예: 3600 → 시작 후 1시간 뒤 스케줄 종료
     * -1이면 무제한
     */
    long endAfterSeconds() default -1;

    // ========================================
    // 공통: 타임존 & Misfire 정책
    // ========================================

    /**
     * 타임존 (비우면 설정 파일의 기본값 사용)
     * 예: "Asia/Seoul", "UTC"
     */
    String timeZone() default "";

    /**
     * Misfire 정책 (스케줄이 놓쳤을 때 처리 방법)
     * - DO_NOTHING: 다음 예정 시간까지 대기
     * - FIRE_AND_PROCEED: 즉시 실행 후 정상 진행
     * - IGNORE_MISFIRES: Misfire 무시하고 모두 실행
     */
    MisfirePolicy misfire() default MisfirePolicy.DO_NOTHING;
}
