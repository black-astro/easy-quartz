package com.gibis.easyquartz.config;

import com.gibis.easyquartz.service.EasyQuartzBackwardRegistrar;
import com.gibis.easyquartz.service.EasyQuartzRegistrar;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EasyQuartz 자동 설정
 *
 * 활성화 조건:
 * - @EnableEasyQuartz 어노테이션이 붙어 있어야 함
 *
 * 커스터마이징:
 * - @Bean으로 직접 등록하면 그게 우선 적용됨 (@ConditionalOnMissingBean)
 *
 * 구조:
 * - EasyQuartzRegistrar: 통합 어노테이션 처리
 * - EasyQuartzBackwardRegistrar: 레거시 어노테이션 처리
 */
@Configuration
@EnableConfigurationProperties(EasyQuartzProperties.class)
@AutoConfigureAfter(QuartzAutoConfiguration.class)  // Quartz 설정 후 실행
public class EasyQuartzAutoConfiguration {

    /**
     * EasyQuartz 설정값 빈
     *
     * 우선순위:
     * 1. 사용자가 @Bean으로 등록한 것
     * 2. application.yml의 easy-quartz 설정
     * 3. 기본값 (enabled=true, timeZone=Asia/Seoul 등)
     */
//    @Bean
//    @ConditionalOnMissingBean
//    public EasyQuartzProperties easyQuartzProperties() {
//        return new EasyQuartzProperties();
//    }

    /**
     * 레거시 어노테이션 처리기
     *
     * 역할:
     * - @EasyQuartzCron
     * - @EasyQuartzCalendarInterval
     * - @EasyQuartzDailyTimeInterval
     * - @EasyQuartzFixedInterval
     * - 공통 유틸리티 메서드 제공
     */
    @Bean
    @ConditionalOnMissingBean
    public EasyQuartzBackwardRegistrar easyQuartzBackwardRegistrar(
            Scheduler scheduler,
            EasyQuartzProperties properties
    ) {
        return new EasyQuartzBackwardRegistrar(scheduler, properties);
    }

    /**
     * 통합 어노테이션 처리기 (메인 등록기)
     *
     * 역할:
     * - @EasyQuartzScheduled 처리
     * - 전체 스캔 조율
     * - BackwardRegistrar에게 레거시 처리 위임
     */
    @Bean
    @ConditionalOnMissingBean
    public EasyQuartzRegistrar easyQuartzRegistrar(
            ApplicationContext applicationContext,
            EasyQuartzProperties properties,
            EasyQuartzBackwardRegistrar backwardRegistrar
    ) {
        return new EasyQuartzRegistrar(applicationContext, properties, backwardRegistrar);
    }
}
