package com.gibis.easyquartz.service;


import com.gibis.easyquartz.config.EasyQuartzProperties;
import com.gibis.easyquartz.enums.CalendarUnit;
import com.gibis.easyquartz.enums.MisfirePolicy;
import com.gibis.easyquartz.enums.SchedulerEngine;
import com.gibis.easyquartz.interfaces.*;
import org.quartz.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * @EasyQuartzScheduled 통합 처리기.
 * <p>
 * - {@code engine}에 따라 Quartz 또는 Spring 등록 경로로 분기합니다.
 * - 레거시 단일 어노테이션(@EasyQuartzCron 등)도 함께 처리합니다.
 * </p>
 */
public class EasyQuartzRegistrar implements SmartInitializingSingleton {

    private final ApplicationContext appCtx;
    private final EasyQuartzProperties props;
    private final EasyQuartzBackwardRegistrar backward;
    private final EasySpringRegistrar springRegistrar;

    public EasyQuartzRegistrar(
            ApplicationContext appCtx,
            EasyQuartzProperties props,
            EasyQuartzBackwardRegistrar backward,
            EasySpringRegistrar springRegistrar
    ) {
        this.appCtx = appCtx;
        this.props = props;
        this.backward = backward;
        this.springRegistrar = springRegistrar;
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

                if (m.isAnnotationPresent(EasyQuartzScheduled.class)) {
                    registerScheduled(beanName, m, m.getAnnotation(EasyQuartzScheduled.class));
                    continue;
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

    private void registerScheduled(String beanName, Method m, EasyQuartzScheduled a) {
        try {
            if (a.engine() == SchedulerEngine.SPRING) {
                springRegistrar.register(beanName, m, a);
                return;
            }
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

        JobDetail job = createJobDetail(jk, a, beanName, m, null);

        TimeZone tz = TimeZone.getTimeZone(backward.resolveTz(a.timeZone()));
        CronScheduleBuilder sb = CronScheduleBuilder.cronSchedule(a.cron()).inTimeZone(tz);
        sb = applyMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withPriority(a.priority()).withSchedule(sb),
                computeStartDelayMs(a),
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

        JobDetail job = createJobDetail(jk, a, beanName, m, null);

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
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withPriority(a.priority()).withSchedule(sb),
                computeStartDelayMs(a),
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

        JobDataMap map = backward.baseJobData(beanName, m);
        applyJobData(map, a.jobData());
        map.put("intervalMs", intervalMs);
        map.put("triggerName", tk.getName());
        map.put("triggerGroup", tk.getGroup());
        map.put("fixedMode", "FIXED_DELAY");
        map.put("endAtEpochMs", calculateEndTime(a.endAfterSeconds()));

        JobDetail job = createJobDetail(jk, a, beanName, m, map);

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger()
                        .withIdentity(tk)
                        .forJob(jk)
                        .withPriority(a.priority())
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)),
                computeStartDelayMs(a),
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

        JobDetail job = createJobDetail(jk, a, beanName, m, null);

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
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withPriority(a.priority()).withSchedule(sb),
                computeStartDelayMs(a),
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

        JobDetail job = createJobDetail(jk, a, beanName, m, null);

        DailyTimeIntervalScheduleBuilder sb = DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                .startingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.dailyStartHour(), a.dailyStartMin()))
                .endingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.dailyEndHour(), a.dailyEndMin()));

        if (intervalSeconds >= 60) {
            sb = sb.withIntervalInMinutes((int) (intervalSeconds / 60));
        } else {
            sb = sb.withIntervalInSeconds((int) intervalSeconds);
        }

        if (a.daysOfWeek() != null && a.daysOfWeek().length > 0) {
            Set<Integer> dows = Arrays.stream(a.daysOfWeek())
                    .mapToInt(backward::toQuartzDow)
                    .boxed()
                    .collect(Collectors.toSet());
            sb = sb.onDaysOfTheWeek(dows);
        }

        sb = applyDailyMisfire(sb, a.misfire());

        Trigger trigger = backward.applyStartEnd(
                TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withPriority(a.priority()).withSchedule(sb),
                computeStartDelayMs(a),
                calculateEndTime(a.endAfterSeconds())
        ).build();

        backward.upsert(job, trigger, tk);
    }

    // ========================================
    // 유틸리티
    // ========================================

    private long calculateInterval(long hours, long minutes, long seconds) {
        return (hours * 3600 + minutes * 60 + seconds) * 1000;
    }

    private long calculateEndTime(long endAfterSeconds) {
        if (endAfterSeconds <= 0) return -1;
        return System.currentTimeMillis() + endAfterSeconds * 1000;
    }

    /**
     * 시작 지연을 계산합니다. jitterSeconds가 양수이면 0~jitter 사이의 임의 값을 추가합니다.
     */
    private long computeStartDelayMs(EasyQuartzScheduled a) {
        long base = a.startDelaySeconds() * 1000L;
        long jitter = a.jitterSeconds();
        if (jitter > 0) {
            base += ThreadLocalRandom.current().nextLong(jitter * 1000L + 1);
        }
        return base;
    }

    private JobDetail createJobDetail(
            JobKey jk,
            EasyQuartzScheduled a,
            String beanName,
            Method m,
            JobDataMap additionalData
    ) {
        JobDataMap map = additionalData != null ? additionalData : backward.baseJobData(beanName, m);
        if (additionalData == null) {
            applyJobData(map, a.jobData());
        }

        return JobBuilder.newJob(backward.jobClass(a.disallowConcurrent()))
                .withIdentity(jk)
                .withDescription(a.description())
                .usingJobData(map)
                .storeDurably(true)
                .requestRecovery(a.requestRecovery())
                .build();
    }

    /**
     * "key=value" 문자열 배열을 JobDataMap에 적용합니다.
     */
    private void applyJobData(JobDataMap map, String[] entries) {
        if (entries == null) return;
        for (String entry : entries) {
            int idx = entry.indexOf('=');
            if (idx <= 0) {
                throw new IllegalArgumentException("jobData entry must be 'key=value' but was: " + entry);
            }
            map.put(entry.substring(0, idx), entry.substring(idx + 1));
        }
    }

    private CronScheduleBuilder applyMisfire(CronScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    private SimpleScheduleBuilder applySimpleMisfire(SimpleScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionNextWithRemainingCount();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionNowWithExistingCount();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    private CalendarIntervalScheduleBuilder applyCalendarMisfire(CalendarIntervalScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }

    private DailyTimeIntervalScheduleBuilder applyDailyMisfire(DailyTimeIntervalScheduleBuilder sb, MisfirePolicy policy) {
        return switch (policy) {
            case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
            case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
            case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
        };
    }
}
