//package com.gibis.sample;
//
//import gibis.enums.CalendarUnit;
//import gibis.enums.Day;
//import gibis.enums.MisfirePolicy;
//import gibis.enums.ScheduleType;
//import gibis.interfaces.EasyQuartzScheduled;
//import gibis.interfaces.EnableEasyQuartz;
//import org.springframework.stereotype.Component;
//
//@Component
//public class DemoJobs {
//// ========================================
//    // 1. CRON 타입
//    // ========================================
//
//    /**
//     * 매시 정각 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CRON,
//            cron = "0 0 * * * ?",
//            description = "매시 정각 배치"
//    )
//    public void hourlyBatch() {
//        System.out.println("매시 정각 실행");
//    }
//
//    /**
//     * 매일 새벽 2시 (Asia/Seoul 기준)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CRON,
//            cron = "0 0 2 * * ?",
//            timeZone = "Asia/Seoul",
//            description = "일일 배치"
//    )
//    public void dailyBatch() {
//        System.out.println("매일 새벽 2시 실행");
//    }
//
//    /**
//     * 평일 오전 9시
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CRON,
//            cron = "0 0 9 ? * MON-FRI",
//            description = "평일 아침 리포트"
//    )
//    public void weekdayMorningReport() {
//        System.out.println("평일 오전 9시 리포트");
//    }
//
//    // ========================================
//    // 2. FIXED_RATE 타입
//    // ========================================
//
//    /**
//     * 3시간마다 실행 (이전 실행 시작 시점 기준)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateHours = 3,
//            description = "3시간마다 동기화"
//    )
//    public void syncEveryThreeHours() {
//        System.out.println("3시간마다 실행");
//    }
//
//    /**
//     * 30분마다 실행 (앱 시작 후 5분 지연)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateMinutes = 30,
//            startDelaySeconds = 300,  // 5분 후 시작
//            description = "30분마다 캐시 갱신"
//    )
//    public void refreshCache() {
//        System.out.println("30분마다 캐시 갱신");
//    }
//
//    /**
//     * 10초마다 실행 (총 100회만)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateSeconds = 10,
//            repeatForever = false,
//            repeatCount = 99,  // 100회 실행 (최초 1회 + 반복 99회)
//            description = "모니터링 (제한적)"
//    )
//    public void limitedMonitoring() {
//        System.out.println("10초마다 실행 (100회까지)");
//    }
//
//    // ========================================
//    // 3. FIXED_DELAY 타입
//    // ========================================
//
//    /**
//     * 작업 종료 후 5분 대기 후 재실행
//     * ⚠️ FIXED_DELAY는 반드시 disallowConcurrent=true 필요
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_DELAY,
//            fixedDelayMinutes = 5,
//            disallowConcurrent = true,  // 필수!
//            description = "작업 종료 후 5분 대기"
//    )
//    public void processWithDelay() {
//        System.out.println("무거운 작업 수행...");
//        // 실행 시간이 가변적인 작업에 적합
//    }
//
//    /**
//     * 1시간 후 종료되는 FIXED_DELAY
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_DELAY,
//            fixedDelaySeconds = 30,
//            endAfterSeconds = 3600,  // 1시간 후 종료
//            disallowConcurrent = true,
//            description = "1시간 동안만 실행"
//    )
//    public void temporaryTask() {
//        System.out.println("30초 간격으로 1시간 동안 실행");
//    }
//
//    // ========================================
//    // 4. CALENDAR 타입
//    // ========================================
//
//    /**
//     * 매주 월요일 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CALENDAR,
//            calendarUnit = CalendarUnit.WEEKS,
//            calendarInterval = 1,
//            description = "주간 리포트"
//    )
//    public void weeklyReport() {
//        System.out.println("매주 실행");
//    }
//
//    /**
//     * 매월 1일 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CALENDAR,
//            calendarUnit = CalendarUnit.MONTHS,
//            calendarInterval = 1,
//            description = "월간 정산"
//    )
//    public void monthlySettlement() {
//        System.out.println("매월 1일 실행");
//    }
//
//    /**
//     * 2주마다 실행 (DST 처리 포함)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CALENDAR,
//            calendarUnit = CalendarUnit.WEEKS,
//            calendarInterval = 2,
//            preserveHourAcrossDst = true,
//            description = "격주 배치"
//    )
//    public void biweeklyBatch() {
//        System.out.println("2주마다 실행");
//    }
//
//    // ========================================
//    // 5. DAILY_TIME 타입
//    // ========================================
//
//    /**
//     * 평일 9-18시, 30분마다 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.DAILY_TIME,
//            dailyStartHour = 9,
//            dailyEndHour = 18,
//            intervalMinutes = 30,
//            daysOfWeek = {Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI},
//            description = "근무시간 모니터링"
//    )
//    public void workHoursMonitoring() {
//        System.out.println("평일 근무시간 중 30분마다 실행");
//    }
//
//    /**
//     * 매일 00:00-06:00, 1시간마다 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.DAILY_TIME,
//            dailyStartHour = 0,
//            dailyEndHour = 6,
//            intervalHours = 1,
//            description = "새벽 정리 작업"
//    )
//    public void nightlyCleanup() {
//        System.out.println("새벽 시간대 1시간마다 실행");
//    }
//
//    /**
//     * 주말 오전 10시-12시, 10분마다 실행
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.DAILY_TIME,
//            dailyStartHour = 10,
//            dailyEndHour = 12,
//            intervalMinutes = 10,
//            daysOfWeek = {Day.SAT, Day.SUN},
//            description = "주말 특별 작업"
//    )
//    public void weekendTask() {
//        System.out.println("주말 오전 10분마다 실행");
//    }
//
//    // ========================================
//    // 6. 고급 옵션 활용
//    // ========================================
//
//    /**
//     * Misfire 정책 활용
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CRON,
//            cron = "0 */5 * * * ?",  // 5분마다
//            misfire = MisfirePolicy.FIRE_AND_PROCEED,  // 놓친 실행 즉시 수행
//            description = "중요 배치 (놓치면 즉시 실행)"
//    )
//    public void criticalBatch() {
//        System.out.println("Misfire 발생 시 즉시 실행");
//    }
//
//    /**
//     * 동시 실행 허용
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateSeconds = 5,
//            disallowConcurrent = false,  // 동시 실행 허용
//            description = "빠른 작업 (동시 실행 가능)"
//    )
//    public void fastTask() {
//        System.out.println("5초마다 실행 (이전 실행과 겹칠 수 있음)");
//    }
//
//    /**
//     * 복합 시간 설정 (2시간 30분 45초마다)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateHours = 2,
//            fixedRateMinutes = 30,
//            fixedRateSeconds = 45,
//            description = "복합 간격 배치"
//    )
//    public void complexInterval() {
//        System.out.println("2시간 30분 45초마다 실행");
//    }
//
//    /**
//     * 시작 지연 + 종료 시간
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateMinutes = 10,
//            startDelaySeconds = 60,    // 1분 후 시작
//            endAfterSeconds = 7200,    // 2시간 후 종료
//            description = "제한 시간 작업"
//    )
//    public void timedTask() {
//        System.out.println("앱 시작 1분 후부터 2시간 동안 10분마다 실행");
//    }
//
//    /**
//     * 타임존 지정 (글로벌 서비스)
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.CRON,
//            cron = "0 0 9 * * ?",
//            timeZone = "America/New_York",
//            description = "뉴욕 시간 기준 오전 9시"
//    )
//    public void newYorkMorning() {
//        System.out.println("뉴욕 시간 오전 9시 실행");
//    }
//
//    /**
//     * 커스텀 그룹 지정
//     */
//    @EasyQuartzScheduled(
//            type = ScheduleType.FIXED_RATE,
//            fixedRateMinutes = 5,
//            group = "REPORT_GROUP",
//            name = "sales-report",
//            description = "매출 리포트 생성"
//    )
//    public void salesReport() {
//        System.out.println("그룹화된 스케줄");
//    }
//}
