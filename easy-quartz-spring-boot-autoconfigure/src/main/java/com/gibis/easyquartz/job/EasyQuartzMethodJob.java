package com.gibis.easyquartz.job;


import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Date;

import static com.gibis.easyquartz.config.EasyQuartzContextAutoConfiguration.SCHEDULER_CTX_APP;

public class EasyQuartzMethodJob implements Job {

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
        Method m = bean.getClass().getMethod(methodName);
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

        // 다음 실행: "완료 시점" + interval
        Date next = new Date(System.currentTimeMillis() + intervalMs);

        JobDataMap map2 = ctx.getMergedJobDataMap();
        long endAtEpochMs = map2.getLongValue("endAtEpochMs"); // JobDataMap에 추가 필요

        if (endAtEpochMs > 0 && next.getTime() > endAtEpochMs) {
            // 종료 시간 초과 시 재스케줄하지 않음
            return;
        }

        // 1회 트리거로 재생성
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