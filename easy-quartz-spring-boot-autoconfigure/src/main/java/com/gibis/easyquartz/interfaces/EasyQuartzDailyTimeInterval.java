package com.gibis.easyquartz.interfaces;



import com.gibis.easyquartz.enums.CronMisfire;
import com.gibis.easyquartz.enums.Day;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyQuartzDailyTimeInterval {
    String name() default "";
    String group() default "DEFAULT";

    int startHour();
    int startMinute() default 0;
    int endHour();
    int endMinute() default 0;

    int intervalMinutes() default -1;
    int intervalSeconds() default -1;

    Day[] daysOfWeek() default {};               // 비우면 매일
    CronMisfire misfire() default CronMisfire.DO_NOTHING;

    String timeZone() default "";
    long startDelayMs() default 0;
    long endAtEpochMs() default -1;

    boolean disallowConcurrent() default true;
    String description() default "";
}
