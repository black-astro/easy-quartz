package com.gibis.easyquartz.config;

import com.gibis.easyquartz.service.EasyQuartzBackwardRegistrar;
import com.gibis.easyquartz.service.EasyQuartzRegistrar;
import com.gibis.easyquartz.service.EasySpringRegistrar;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * EasyQuartz 자동 설정.
 *
 * <p>활성화 조건: 사용자의 설정에 {@code @EnableEasyQuartz}가 있어야 함.</p>
 *
 * <p>커스터마이징: 사용자가 같은 타입의 빈을 직접 등록하면 그것이 우선합니다 ({@link ConditionalOnMissingBean}).</p>
 */
@Configuration
@EnableConfigurationProperties(EasyQuartzProperties.class)
@AutoConfigureAfter(QuartzAutoConfiguration.class)
public class EasyQuartzAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EasyQuartzBackwardRegistrar easyQuartzBackwardRegistrar(
            Scheduler scheduler,
            EasyQuartzProperties properties
    ) {
        return new EasyQuartzBackwardRegistrar(scheduler, properties);
    }

    /**
     * Spring 엔진용 TaskScheduler.
     * <p>
     * 사용자가 별도 TaskScheduler를 등록한 경우 그것을 사용하고, 없으면 라이브러리 전용 ThreadPoolTaskScheduler를 생성합니다.
     * 이는 {@code @EnableScheduling}으로 등록되는 ScheduledAnnotationBeanPostProcessor와 격리되며,
     * 사용자의 기존 {@code @Scheduled}와 간섭하지 않습니다.
     * </p>
     */
    @Bean(name = "easyQuartzSpringTaskScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "easyQuartzSpringTaskScheduler")
    public ThreadPoolTaskScheduler easyQuartzSpringTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setThreadNamePrefix("easy-quartz-spring-");
        s.setPoolSize(4);
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(30);
        s.initialize();
        return s;
    }

    @Bean
    @ConditionalOnMissingBean
    public EasySpringRegistrar easySpringRegistrar(
            ApplicationContext applicationContext,
            EasyQuartzProperties properties,
            ObjectProvider<TaskScheduler> userTaskScheduler,
            ThreadPoolTaskScheduler easyQuartzSpringTaskScheduler
    ) {
        TaskScheduler ts = userTaskScheduler.getIfUnique(() -> easyQuartzSpringTaskScheduler);
        return new EasySpringRegistrar(applicationContext, properties, ts);
    }

    @Bean
    @ConditionalOnMissingBean
    public EasyQuartzRegistrar easyQuartzRegistrar(
            ApplicationContext applicationContext,
            EasyQuartzProperties properties,
            EasyQuartzBackwardRegistrar backwardRegistrar,
            EasySpringRegistrar springRegistrar
    ) {
        return new EasyQuartzRegistrar(applicationContext, properties, backwardRegistrar, springRegistrar);
    }
}
