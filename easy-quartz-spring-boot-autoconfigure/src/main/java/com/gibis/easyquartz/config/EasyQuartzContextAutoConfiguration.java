package com.gibis.easyquartz.config;


import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@AutoConfiguration
@ConditionalOnClass({Scheduler.class, QuartzAutoConfiguration.class})
@EnableConfigurationProperties(EasyQuartzProperties.class)
@ConditionalOnProperty(prefix = "easy.quartz", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(QuartzAutoConfiguration.class)
public class EasyQuartzContextAutoConfiguration {

    public static final String SCHEDULER_CTX_APP = "easyQuartzAppCtx";

    @Bean
    public SchedulerFactoryBeanCustomizer easyQuartzSchedulerContextCustomizer(ApplicationContext appCtx) {
        return factory -> factory.setSchedulerContextAsMap(Map.of(SCHEDULER_CTX_APP, appCtx));
    }
}
