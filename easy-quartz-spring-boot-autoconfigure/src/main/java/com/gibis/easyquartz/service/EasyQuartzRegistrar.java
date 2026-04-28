package com.gibis.easyquartz.service;


import com.gibis.easyquartz.config.EasyQuartzProperties;
import com.gibis.easyquartz.enums.CalendarUnit;
import com.gibis.easyquartz.enums.MisfirePolicy;
import com.gibis.easyquartz.interfaces.*;
import org.quartz.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * - 통합 어노테이션 @EasyQuartzScheduled 지원
 * - 기존 개별 어노테이션도 하위 호환성 유지
 */
public class EasyQuartzRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext appCtx;
    private final EasyQuartzProperties props;
    private final EasyQuartzBackwardRegistrar backward;

    public EasyQuartzRegistrar(ApplicationContext appCtx, EasyQuartzProperties props, EasyQuartzBackwardRegistrar backward) {
        this.appCtx = appCtx;
        this.props = props;
        this.backward = backward;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!props.isEnabled()) return;

        for (String beanName : appCtx.getBeanDefinitionNames()) {
            Object bean = appCtx.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            if (!backward.isInScanPackages(targetClass)) continue;

            for (Method m : targetClass.getMethods()) {
                if (!backward.isSupportedSignature(m)) continue;

                // 통합 어노테이션 우선 처리
                if (m.isAnnotationPresent(EasyQuartzScheduled.class)) {
                    registerScheduled(beanName, m, m.getAnnotation(EasyQuartzScheduled.class));
                    continue; // 통합 어노테이션이 있으면 개별 어노테이션은 무시
                }

                if (m.isAnnotationPresent(EasyQuartzCron.class)) {
                    backward.registerCron(beanName, m, m.getAnnotation(EasyQuartzCron.class));
                }
                if (m.isAnnotationPresent(EasyQuartzCalendarInterval.class)) {
                    backward.registerCalendar(beanName, m, m.getAnnotation(EasyQuartzCalendarInterval.class));
                }
                if (m.isAnnotationPresent(EasyQuartzDailyTimeInterval.class)) {
                    backward.registerDaily(beanName, m, m.getAnnotation(EasyQuartzDailyTimeInterval.class));
                }
                if (m.isAnnotationPresent(EasyQuartzFixedInterval.class)) {
                    backward.registerFixed(beanName, m, m.getAnnotation(EasyQuartzFixedInterval.class));
                }
            }
        }
    }

    // ========================================
    // 새로운 통합 어노테이션 처리
    // ========================================

    private void registerScheduled(String beanName, Method m, EasyQuartzScheduled a) {
        try {
            switch (a.type()) {
                case CRON -> registerScheduledCron(beanName, m, a);
                case FIXED_RATE -> registerScheduledFixedRate(beanName, m, a);
                case FIXED_DELAY -> registerScheduledFixedDelay(beanName, m, a);
                case CALENDAR -> registerScheduledCalendar(beanName, m, a);
                case DAILY_TIME -> registerScheduledDailyTime(beanName, m, a);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to register scheduled method: " + beanName + "." + m.getName(), e);
        }
    }

    // ========================================
    // CRON 타입 처리
    // ========================================

    private void registerScheduledCron(String beanName, Method m, EasyQuartzScheduled a) throws SchedulerException {
        if (a.cron().isBlank()) {
            throw new IllegalArgumentException("cron expression is required for CRON type");
        }

        String base = backward.resolveName(a.name(), beanName, m);
        JobKey jk = JobKey.jobKey(base + ".job", a.group());
        TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

        JobDetail job = createJobDetail(jk, a.description(), a.disallowConcurrent(), beanName, m, null);

        TimeZone tz = TimeZone.getTimeZone(backward.resolveTz(a.timeZone()));
        CronScheduleBuilder sb = CronScheduleBuilder.cronSchedule(a.cron()).inTimeZone(tz);
        sb = applyMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withSchedule(sb),
                toMillis(a.startDelaySeconds()),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }

    // ========================================
    // FIXED_RATE 타입 처리
    // ========================================

    private void registerScheduledFixedRate(String beanName, Method m, EasyQuartzScheduled a) throws SchedulerException {
        long intervalMs = calculateInterval(a.fixedRateHours(), a.fixedRateMinutes(), a.fixedRateSeconds());
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("FIXED_RATE requires positive interval");
        }

        String base = backward.resolveName(a.name(), beanName, m);
        JobKey jk = JobKey.jobKey(base + ".job", a.group());
        TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

        JobDetail job = createJobDetail(jk, a.description(), a.disallowConcurrent(), beanName, m, null);

        SimpleScheduleBuilder sb = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(intervalMs);

        if (a.repeatForever()) {
            sb = sb.repeatForever();
        } else {
            if (a.repeatCount() < 0) {
                throw new IllegalArgumentException("repeatCount required when repeatForever=false");
            }
            sb = sb.withRepeatCount(a.repeatCount());
        }

        sb = applySimpleMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withSchedule(sb),
                toMillis(a.startDelaySeconds()),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }

    // ========================================
    // FIXED_DELAY 타입 처리
    // ========================================

    private void registerScheduledFixedDelay(String beanName, Method m, EasyQuartzScheduled a) throws SchedulerException {
        if (!a.disallowConcurrent()) {
            throw new IllegalStateException("FIXED_DELAY requires disallowConcurrent=true for safety");
        }

        long intervalMs = calculateInterval(a.fixedDelayHours(), a.fixedDelayMinutes(), a.fixedDelaySeconds());
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("FIXED_DELAY requires positive interval");
        }

        String base = backward.resolveName(a.name(), beanName, m);
        JobKey jk = JobKey.jobKey(base + ".job", a.group());
        TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

        // ✅ FIXED_DELAY는 Job에서 다음 실행을 스케줄링하므로 JobDataMap에 정보 저장
        JobDataMap map = backward.baseJobData(beanName, m);
        map.put("intervalMs", intervalMs);
        map.put("triggerName", tk.getName());
        map.put("triggerGroup", tk.getGroup());
        map.put("fixedMode", "FIXED_DELAY");
        map.put("endAtEpochMs", calculateEndTime(a.endAfterSeconds()));

        JobDetail job = createJobDetail(jk, a.description(), true, beanName, m, map);

        // 첫 실행은 SimpleTrigger로 한 번만
        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger()
                        .withIdentity(tk)
                        .forJob(jk)
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)),
                toMillis(a.startDelaySeconds()),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }

    // ========================================
    // CALENDAR 타입 처리
    // ========================================

    private void registerScheduledCalendar(String beanName, Method m, EasyQuartzScheduled a) throws SchedulerException {
        if (a.calendarUnit() == CalendarUnit.NONE) {
            throw new IllegalArgumentException("calendarUnit is required for CALENDAR type");
        }

        String base = backward.resolveName(a.name(), beanName, m);
        JobKey jk = JobKey.jobKey(base + ".job", a.group());
        TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

        JobDetail job = createJobDetail(jk, a.description(), a.disallowConcurrent(), beanName, m, null);

        TimeZone tz = TimeZone.getTimeZone(backward.resolveTz(a.timeZone()));
        CalendarIntervalScheduleBuilder sb = CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                .inTimeZone(tz)
                .preserveHourOfDayAcrossDaylightSavings(a.preserveHourAcrossDst())
                .skipDayIfHourDoesNotExist(a.skipDayIfHourDoesNotExist());

        sb = switch (a.calendarUnit()) {
            case DAYS -> sb.withIntervalInDays(a.calendarInterval());
            case WEEKS -> sb.withIntervalInWeeks(a.calendarInterval());
            case MONTHS -> sb.withIntervalInMonths(a.calendarInterval());
            case NONE -> throw new IllegalStateException("Unreachable");
        };

        sb = applyCalendarMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withSchedule(sb),
                toMillis(a.startDelaySeconds()),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }

    // ========================================
    // DAILY_TIME 타입 처리
    // ========================================

    private void registerScheduledDailyTime(String beanName, Method m, EasyQuartzScheduled a) throws SchedulerException {
        if (a.dailyStartHour() < 0 || a.dailyEndHour() < 0) {
            throw new IllegalArgumentException("dailyStartHour and dailyEndHour are required for DAILY_TIME type");
        }

        long intervalSeconds = a.intervalHours() * 3600L + a.intervalMinutes() * 60L + a.intervalSeconds();
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("DAILY_TIME requires positive interval");
        }

        String base = backward.resolveName(a.name(), beanName, m);
        JobKey jk = JobKey.jobKey(base + ".job", a.group());
        TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

        JobDetail job = createJobDetail(jk, a.description(), a.disallowConcurrent(), beanName, m, null);

        DailyTimeIntervalScheduleBuilder sb = DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                .startingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.dailyStartHour(), a.dailyStartMin()))
                .endingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.dailyEndHour(), a.dailyEndMin()));

        // 간격 설정 (초 단위 우선)
        if (intervalSeconds >= 60) {
            sb = sb.withIntervalInMinutes((int) (intervalSeconds / 60));
        } else {
            sb = sb.withIntervalInSeconds((int) intervalSeconds);
        }

        // 요일 설정
        if (a.daysOfWeek() != null && a.daysOfWeek().length > 0) {
            Set<Integer> dows = Arrays.stream(a.daysOfWeek())
                    .mapToInt(backward::toQuartzDow)
                    .boxed()
                    .collect(Collectors.toSet());
            sb = sb.onDaysOfTheWeek(dows);
        }

        sb = applyDailyMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withSchedule(sb),
                toMillis(a.startDelaySeconds()),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }
    // ========================================
    // 유틸리티 메서드
    // ========================================

    /**
     * 시/분/초를 밀리초로 변환
     */
    private long calculateInterval(long hours, long minutes, long seconds) {
        return (hours * 3600 + minutes * 60 + seconds) * 1000;
    }

    /**
     * 초를 밀리초로 변환
     */
    private long toMillis(long seconds) {
        return seconds * 1000;
    }

    /**
     * 종료 시간 계산 (시작 시점 기준)
     */
    private long calculateEndTime(long endAfterSeconds) {
        if (endAfterSeconds <= 0) return -1;
        return System.currentTimeMillis() + endAfterSeconds * 1000;
    }

    /**
     * JobDetail 생성
     */
    private JobDetail createJobDetail(
            JobKey jk,
            String description,
            boolean disallowConcurrent,
            String beanName,
            Method m,
            JobDataMap additionalData
    ) {
        JobDataMap map = additionalData != null ? additionalData : backward.baseJobData(beanName, m);
        if (additionalData == null) {
            map = backward.baseJobData(beanName, m);
        }

        return JobBuilder.newJob(backward.jobClass(disallowConcurrent))
                .withIdentity(jk)
                .withDescription(description)
                .usingJobData(map)
                .storeDurably(true)
                .build();
    }

    /**
     * Misfire 정책 적용 (CronScheduleBuilder)
     */
    private CronScheduleBuilder applyMisfire(CronScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    /**
     * Misfire 정책 적용 (SimpleScheduleBuilder)
     */
    private SimpleScheduleBuilder applySimpleMisfire(SimpleScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionNextWithRemainingCount();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionNowWithExistingCount();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    /**
     * Misfire 정책 적용 (CalendarIntervalScheduleBuilder)
     */
    private CalendarIntervalScheduleBuilder applyCalendarMisfire(CalendarIntervalScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    /**
     * Misfire 정책 적용 (DailyTimeIntervalScheduleBuilder)
     */
    private DailyTimeIntervalScheduleBuilder applyDailyMisfire(DailyTimeIntervalScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }
}
