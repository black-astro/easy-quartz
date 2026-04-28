package com.gibis.easyquartz.service;

import com.gibis.easyquartz.config.EasyQuartzProperties;
import com.gibis.easyquartz.interfaces.EasyQuartzScheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring TaskScheduler 기반 등록기.
 *
 * <p>
 * {@code engine = SchedulerEngine.SPRING}로 지정된 메서드를 처리합니다.
 * CRON / FIXED_RATE / FIXED_DELAY 세 가지 타입만 지원하며 misfire/클러스터링/영속화는 제공하지 않습니다.
 * </p>
 */
public class EasySpringRegistrar implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EasySpringRegistrar.class);

    private final ApplicationContext appCtx;
    private final EasyQuartzProperties props;
    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();

    public EasySpringRegistrar(ApplicationContext appCtx, EasyQuartzProperties props, TaskScheduler taskScheduler) {
        this.appCtx = appCtx;
        this.props = props;
        this.taskScheduler = taskScheduler;
    }

    public void register(String beanName, Method targetMethod, EasyQuartzScheduled a) {
        String key = beanName + "." + targetMethod.getName();
        Runnable task = () -> invoke(beanName, targetMethod.getName());

        long initialDelayMs = a.startDelaySeconds() * 1000L
                + (a.jitterSeconds() > 0 ? ThreadLocalRandom.current().nextLong(a.jitterSeconds() * 1000L + 1) : 0L);
        Instant startAt = Instant.now().plusMillis(initialDelayMs);

        ScheduledFuture<?> future = switch (a.type()) {
            case CRON -> {
                if (a.cron().isBlank()) {
                    throw new IllegalArgumentException("cron expression is required for CRON type on " + key);
                }
                String tzId = a.timeZone().isBlank() ? props.getDefaultTimeZone() : a.timeZone();
                yield taskScheduler.schedule(task, new CronTrigger(a.cron(), TimeZone.getTimeZone(tzId).toZoneId()));
            }
            case FIXED_RATE -> {
                long intervalMs = totalMs(a.fixedRateHours(), a.fixedRateMinutes(), a.fixedRateSeconds());
                if (intervalMs <= 0) {
                    throw new IllegalArgumentException("FIXED_RATE requires positive interval on " + key);
                }
                yield taskScheduler.scheduleAtFixedRate(task, startAt, Duration.ofMillis(intervalMs));
            }
            case FIXED_DELAY -> {
                long intervalMs = totalMs(a.fixedDelayHours(), a.fixedDelayMinutes(), a.fixedDelaySeconds());
                if (intervalMs <= 0) {
                    throw new IllegalArgumentException("FIXED_DELAY requires positive interval on " + key);
                }
                yield taskScheduler.scheduleWithFixedDelay(task, startAt, Duration.ofMillis(intervalMs));
            }
            case CALENDAR, DAILY_TIME -> throw new IllegalStateException(
                    "ScheduleType " + a.type() + " is supported only on the QUARTZ engine: " + key);
        };

        ScheduledFuture<?> previous = scheduled.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }

        if (a.endAfterSeconds() > 0) {
            taskScheduler.schedule(() -> {
                ScheduledFuture<?> f = scheduled.remove(key);
                if (f != null) f.cancel(false);
            }, Instant.now().plusSeconds(a.endAfterSeconds()));
        }
    }

    /**
     * Spring AOP 프록시(트랜잭션, 캐시 등)가 적용되도록 빈의 실제 클래스에서 메서드를 lookup하여 호출합니다.
     * 결과는 ConcurrentHashMap에 캐시되어 매 실행마다 reflection lookup을 반복하지 않습니다.
     */
    private void invoke(String beanName, String methodName) {
        Object bean = appCtx.getBean(beanName);
        Class<?> beanClass = bean.getClass();
        Method method = methodCache.computeIfAbsent(beanClass.getName() + "#" + methodName, k -> {
            try {
                return beanClass.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "Method not found on bean: " + beanClass.getName() + "." + methodName, e);
            }
        });
        try {
            method.invoke(bean);
        } catch (InvocationTargetException e) {
            log.error("Spring-engine scheduled task failed: {}.{}", beanName, methodName, e.getTargetException());
        } catch (Exception e) {
            log.error("Spring-engine scheduled task invocation error: {}.{}", beanName, methodName, e);
        }
    }

    private static long totalMs(long h, long mi, long s) {
        return (h * 3600 + mi * 60 + s) * 1000L;
    }

    @Override
    public void destroy() {
        scheduled.values().forEach(f -> f.cancel(false));
        scheduled.clear();
        methodCache.clear();
    }
}
