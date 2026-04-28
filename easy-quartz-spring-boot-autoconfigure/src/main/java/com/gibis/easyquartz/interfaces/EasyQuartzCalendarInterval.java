package com.gibis.easyquartz.interfaces;

import com.gibis.easyquartz.enums.CronMisfire;
import com.gibis.easyquartz.enums.IntervalUnit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyQuartzCalendarInterval {
    String name() default "";
    String group() default "DEFAULT";

    IntervalUnit unit();
    int interval();                              // 1,2,...

    boolean preserveHourAcrossDst() default true;
    boolean skipDayIfHourDoesNotExist() default true;

    CronMisfire misfire() default CronMisfire.DO_NOTHING; // Quartz가 제공하는 3종 패턴 재사용

    long startDelayMs() default 0;
    long endAtEpochMs() default -1;

    String timeZone() default "";
    boolean disallowConcurrent() default true;
    String description() default "";
}
