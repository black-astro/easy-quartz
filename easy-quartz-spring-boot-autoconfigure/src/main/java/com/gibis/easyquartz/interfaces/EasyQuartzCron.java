package com.gibis.easyquartz.interfaces;

import com.gibis.easyquartz.enums.CronMisfire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyQuartzCron {
    String name() default "";
    String group() default "DEFAULT";

    String cron();
    String timeZone() default "";               // 비우면 global default 사용
    CronMisfire misfire() default CronMisfire.DO_NOTHING;

    long startDelayMs() default 0;
    long endAtEpochMs() default -1;

    boolean disallowConcurrent() default true;
    String description() default "";
}
