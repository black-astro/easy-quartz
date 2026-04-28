package com.gibis.easyquartz.interfaces;

import com.gibis.easyquartz.enums.FixedMode;
import com.gibis.easyquartz.enums.SimpleMisfire;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyQuartzFixedInterval {
    String name() default "";
    String group() default "DEFAULT";

    long intervalMs();
    FixedMode mode() default FixedMode.CATCH_UP;

    // Simple에서만 횟수 제한
    boolean repeatForever() default true;
    int repeatCount() default -1;                // repeatForever=false일 때만

    // CATCH_UP일 때 misfire 옵션
    SimpleMisfire simpleMisfire() default SimpleMisfire.NOW_WITH_EXISTING_COUNT;

    long startDelayMs() default 0;
    long endAtEpochMs() default -1;

    boolean disallowConcurrent() default true;
    String description() default "";
}
