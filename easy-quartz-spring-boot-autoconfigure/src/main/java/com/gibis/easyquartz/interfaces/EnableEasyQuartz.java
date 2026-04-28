package com.gibis.easyquartz.interfaces;

import com.gibis.easyquartz.config.EasyQuartzAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EasyQuartzAutoConfiguration.class)
public @interface EnableEasyQuartz {
}
