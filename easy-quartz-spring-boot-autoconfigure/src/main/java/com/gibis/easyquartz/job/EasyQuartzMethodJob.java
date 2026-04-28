package com.gibis.easyquartz.job;


import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static com.gibis.easyquartz.config.EasyQuartzContextAutoConfiguration.SCHEDULER_CTX_APP;

public class EasyQuartzMethodJob implements Job {

    /**
     * Reflection lookup 비용을 매 실행마다 반복하지 않도록 (beanName + "#" + methodName) 키 기반으로 Method를 캐시합니다.
     */
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            invokeTarget(context);
            handleFixedDelayIfNeeded(context);
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    protected void invokeTarget(JobExecutionContext ctx) throws Exception {
        ApplicationContext app =
                (ApplicationContext) ctx.getScheduler().getContext().get(SCHEDULER_CTX_APP);

        if (app == null) {
            throw new IllegalStateException("ApplicationContext not found in SchedulerContext");
        }

        JobDataMap map = ctx.getMergedJobDataMap();
        String beanName = map.getString("beanName");
        String methodName = map.getString("methodName");

        Object bean = app.getBean(beanName);
        Class<?> beanClass = bean.getClass();

        Method m = METHOD_CACHE.computeIfAbsent(beanClass.getName() + "#" + methodName, key -> {
            try {
                return beanClass.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                        "Method not found: " + beanClass.getName() + "." + methodName, e);
            }
        });

        m.invoke(bean);
    }

    protected void handleFixedDelayIfNeeded(JobExecutionContext ctx) throws SchedulerException {
        JobDataMap map = ctx.getMergedJobDataMap();
        String mode = map.getString("fixedMode");
        if (!"FIXED_DELAY".equals(mode)) return;

        long intervalMs = map.getLong("intervalMs");

        String triggerName = map.getString("triggerName");
        String triggerGroup = map.getString("triggerGroup");
        TriggerKey tk = TriggerKey.triggerKey(triggerName, triggerGroup);

        Date next = new Date(System.currentTimeMillis() + intervalMs);

        long endAtEpochMs = map.getLongValue("endAtEpochMs");

        if (endAtEpochMs > 0 && next.getTime() > endAtEpochMs) {
            return;
        }

        Trigger newTrigger = TriggerBuilder.newTrigger()
                .withIdentity(tk)
                .forJob(ctx.getJobDetail().getKey())
                .startAt(next)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withRepeatCount(0))
                .build();

        ctx.getScheduler().rescheduleJob(tk, newTrigger);
    }
}
