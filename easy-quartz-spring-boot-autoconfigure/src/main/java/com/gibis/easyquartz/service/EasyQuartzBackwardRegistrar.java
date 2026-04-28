package com.gibis.easyquartz.service;


import com.gibis.easyquartz.config.EasyQuartzProperties;
import com.gibis.easyquartz.enums.CalendarUnit;
import com.gibis.easyquartz.enums.Day;
import com.gibis.easyquartz.enums.FixedMode;
import com.gibis.easyquartz.enums.MisfirePolicy;
import com.gibis.easyquartz.interfaces.*;
import com.gibis.easyquartz.job.EasyQuartzMethodJob;
import com.gibis.easyquartz.job.EasyQuartzMethodNonConcurrentJob;
import org.quartz.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * - 통합 어노테이션 @EasyQuartzScheduled 지원
 * - 기존 개별 어노테이션도 하위 호환성 유지
 */
public class EasyQuartzBackwardRegistrar  {

    private final Scheduler scheduler;
    private final EasyQuartzProperties props;

    public EasyQuartzBackwardRegistrar(Scheduler scheduler, EasyQuartzProperties props) {
        this.scheduler = scheduler;
        this.props = props;
    }

    // ========================================
    // 기존 메서드들 (하위 호환성 유지)
    // ========================================

    public boolean isInScanPackages(Class<?> c) {
        if (props.getScanPackages() == null || props.getScanPackages().isEmpty()) return true;
        String pkg = c.getPackageName();
        return props.getScanPackages().stream().anyMatch(pkg::startsWith);
    }

    public boolean isSupportedSignature(Method m) {
        return Modifier.isPublic(m.getModifiers())
                && m.getParameterCount() == 0
                && m.getReturnType() == void.class;
    }

    public Class<? extends Job> jobClass(boolean disallowConcurrent) {
        return disallowConcurrent
                ? EasyQuartzMethodNonConcurrentJob.class
                : EasyQuartzMethodJob.class;
    }

    // 공통 JobDataMap 생성
    public JobDataMap baseJobData(String beanName, Method m) {
        JobDataMap map = new JobDataMap();
        //map.put("appCtx", appCtx);
        map.put("beanName", beanName);
        map.put("methodName", m.getName());
        return map;
    }

    // 공통 start/end 처리
    public <T extends Trigger> TriggerBuilder<T> applyStartEnd(
            TriggerBuilder<T> tb,
            long startDelayMs,
            long endAtEpochMs
    ) {
        if (startDelayMs > 0) {
            tb = tb.startAt(new Date(System.currentTimeMillis() + startDelayMs));
        }
        if (endAtEpochMs > 0) {
            tb = tb.endAt(new Date(endAtEpochMs));
        }
        return tb;
    }

    // ==== registerXXX 들은 아래처럼 타입별로 구현 ====
    public void registerCron(String beanName, Method m, EasyQuartzCron a) {
        try {
            String base = resolveName(a.name(), beanName, m);
            JobKey jk = JobKey.jobKey(base + ".job", a.group());
            TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", a.group());

            JobDetail job = JobBuilder.newJob(jobClass(a.disallowConcurrent()))
                    .withIdentity(jk)
                    .withDescription(a.description())
                    .usingJobData(baseJobData(beanName, m))
                    .storeDurably(true)
                    .build();

            TimeZone tz = TimeZone.getTimeZone(resolveTz(a.timeZone()));
            CronScheduleBuilder sb = CronScheduleBuilder.cronSchedule(a.cron()).inTimeZone(tz);
            sb = switch (a.misfire()) {
                case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
                case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
                case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
            };

            Trigger trigger = applyStartEnd(
                    TriggerBuilder.newTrigger()
                            .withIdentity(tk)
                            .forJob(jk)
                            .withSchedule(sb),
                    a.startDelayMs(),
                    a.endAtEpochMs()
            ).build();

            upsert(job, trigger, tk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerFixed(String beanName, Method m, EasyQuartzFixedInterval a) {
        try {
            if (a.mode() == FixedMode.FIXED_DELAY && !a.disallowConcurrent()) {
                throw new IllegalStateException("FIXED_DELAY requires disallowConcurrent=true for safety");
            }

            String base = resolveName(a.name(), beanName, m);
            String group = a.group();
            JobKey jk = JobKey.jobKey(base + ".job", group);
            TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", group);

            JobDataMap map = baseJobData(beanName, m);
            map.put("intervalMs", a.intervalMs());
            map.put("triggerName", tk.getName());
            map.put("triggerGroup", tk.getGroup());
            map.put("fixedMode", a.mode().name());
            map.put("endAtEpochMs", a.endAtEpochMs());

            JobDetail job = JobBuilder.newJob(jobClass(a.disallowConcurrent()))
                    .withIdentity(jk)
                    .withDescription(a.description())
                    .usingJobData(map)
                    .storeDurably(true)
                    .build();

            Trigger trigger;

            if (a.mode() == FixedMode.FIXED_DELAY) {
                trigger = applyStartEnd(
                        TriggerBuilder.newTrigger()
                                .withIdentity(tk)
                                .forJob(jk)
                                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)),
                        a.startDelayMs(),
                        a.endAtEpochMs()
                ).build();
            } else {
                // CATCH_UP(SimpleTrigger 반복 + misfire)
                SimpleScheduleBuilder sb = SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds((int) a.intervalMs());

                if (a.repeatForever()) {
                    sb = sb.repeatForever();
                } else {
                    if (a.repeatCount() < 0) throw new IllegalArgumentException("repeatCount required");
                    sb = sb.withRepeatCount(a.repeatCount());
                }

                sb = switch (a.simpleMisfire()) {
                    case NOW_WITH_EXISTING_COUNT -> sb.withMisfireHandlingInstructionNowWithExistingCount();
                    case NEXT_WITH_REMAINING_COUNT -> sb.withMisfireHandlingInstructionNextWithRemainingCount();
                    case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
                };

                trigger = applyStartEnd(
                        TriggerBuilder.newTrigger()
                                .withIdentity(tk)
                                .forJob(jk)
                                .withSchedule(sb),
                        a.startDelayMs(),
                        a.endAtEpochMs()
                ).build();
            }

            upsert(job, trigger, tk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Calendar/Daily도 같은 패턴으로 buildSchedule만 다르게 구현하면 됨
    public void upsert(JobDetail job, Trigger trigger, TriggerKey tk) throws SchedulerException {
        boolean exists = scheduler.checkExists(job.getKey());

        if (!exists) {
            scheduler.scheduleJob(job, trigger);
            return;
        }

        // 이미 존재하면 updateExisting 여부에 따라 reschedule
        if (props.isUpdateExisting()) {
            scheduler.addJob(job, true, true);
            scheduler.rescheduleJob(tk, trigger);
        }
    }

    public String resolveName(String explicit, String beanName, Method m) {
        if (explicit != null && !explicit.isBlank()) return explicit;
        return beanName + "." + m.getName();
    }

    public String resolveTz(String tz) {
        return (tz == null || tz.isBlank()) ? props.getDefaultTimeZone() : tz;
    }


    /*
    지원되는 옵션
    매일/매주/매월 (interval 값 포함)
    DST 관련 옵션 2개
    misfire 3종
    timezone / startDelay / endAt
    disallowConcurrent
    * */
    public void registerCalendar(String beanName, Method m, EasyQuartzCalendarInterval a) {
        try {
            String base = resolveName(a.name(), beanName, m);
            String group = a.group();
            JobKey jk = JobKey.jobKey(base + ".job", group);
            TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", group);

            JobDetail job = JobBuilder.newJob(jobClass(a.disallowConcurrent()))
                    .withIdentity(jk)
                    .withDescription(a.description())
                    .usingJobData(baseJobData(beanName, m))
                    .storeDurably(true)
                    .build();

            // TimeZone (CalendarInterval은 inTimeZone 지원)
            TimeZone tz = TimeZone.getTimeZone(resolveTz(a.timeZone()));

            CalendarIntervalScheduleBuilder sb = CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                    .inTimeZone(tz)
                    .preserveHourOfDayAcrossDaylightSavings(a.preserveHourAcrossDst())
                    .skipDayIfHourDoesNotExist(a.skipDayIfHourDoesNotExist());

            // interval 설정 (days/weeks/months 중 하나)
            sb = switch (a.unit()) {
                case DAYS -> sb.withIntervalInDays(a.interval());
                case WEEKS -> sb.withIntervalInWeeks(a.interval());
                case MONTHS -> sb.withIntervalInMonths(a.interval());
            };

            // misfire (Quartz 버전/빌더에 따라 메서드 명이 다를 수 있어서 아래 3개는 보통 안전)
            sb = switch (a.misfire()) {
                case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
                case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
                case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
            };

            Trigger trigger = applyStartEnd(
                    TriggerBuilder.newTrigger()
                            .withIdentity(tk)
                            .forJob(jk)
                            .withSchedule(sb),
                    a.startDelayMs(),
                    a.endAtEpochMs()
            ).build();

            upsert(job, trigger, tk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerDaily(String beanName, Method m, EasyQuartzDailyTimeInterval a) {
        try {
            String base = resolveName(a.name(), beanName, m);
            String group = a.group();
            JobKey jk = JobKey.jobKey(base + ".job", group);
            TriggerKey tk = TriggerKey.triggerKey(base + ".trigger", group);

            JobDetail job = JobBuilder.newJob(jobClass(a.disallowConcurrent()))
                    .withIdentity(jk)
                    .withDescription(a.description())
                    .usingJobData(baseJobData(beanName, m))
                    .storeDurably(true)
                    .build();

            // ✅ Quartz 2.3.2: TimeOfDay는 org.quartz.TimeOfDay 사용
            DailyTimeIntervalScheduleBuilder sb = DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                    .startingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.startHour(), a.startMinute()))
                    .endingDailyAt(TimeOfDay.hourAndMinuteOfDay(a.endHour(), a.endMinute()));

            // interval (minutes or seconds)
            if (a.intervalMinutes() > 0) {
                sb = sb.withIntervalInMinutes(a.intervalMinutes());
            } else if (a.intervalSeconds() > 0) {
                sb = sb.withIntervalInSeconds(a.intervalSeconds());
            } else {
                throw new IllegalArgumentException("DailyTimeInterval requires intervalMinutes or intervalSeconds");
            }

            if (a.daysOfWeek() != null && a.daysOfWeek().length > 0) {
                Set<Integer> dows = Arrays.stream(a.daysOfWeek())
                        .mapToInt(this::toQuartzDow)   // IntStream
                        .boxed()                      // IntStream -> Stream<Integer>
                        .collect(Collectors.toSet());

                sb = sb.onDaysOfTheWeek(dows);

            }

            // misfire
            sb = switch (a.misfire()) {
                case DO_NOTHING -> sb.withMisfireHandlingInstructionDoNothing();
                case FIRE_AND_PROCEED -> sb.withMisfireHandlingInstructionFireAndProceed();
                case IGNORE_MISFIRES -> sb.withMisfireHandlingInstructionIgnoreMisfires();
            };

            Trigger trigger = applyStartEnd(
                    TriggerBuilder.newTrigger()
                            .withIdentity(tk)
                            .forJob(jk)
                            .withSchedule(sb),
                    a.startDelayMs(),
                    a.endAtEpochMs()
            ).build();

            upsert(job, trigger, tk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int toQuartzDow(Day d) {
        return switch (d) {
            case SUN -> DateBuilder.SUNDAY;
            case MON -> DateBuilder.MONDAY;
            case TUE -> DateBuilder.TUESDAY;
            case WED -> DateBuilder.WEDNESDAY;
            case THU -> DateBuilder.THURSDAY;
            case FRI -> DateBuilder.FRIDAY;
            case SAT -> DateBuilder.SATURDAY;
        };
    }

}
