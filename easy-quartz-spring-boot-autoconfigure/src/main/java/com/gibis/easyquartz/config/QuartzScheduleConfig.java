//package com.gibis.config;
//
//import com.gibis.job.QuartzJob;
//import org.quartz.*;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Map;
//
//@Configuration
//public class QuartzScheduleConfig {
//
//    @Bean
//    public JobDetail workerJobDetail1() { return buildJobDetail(1); }
//    @Bean
//    public JobDetail workerJobDetail2() { return buildJobDetail(2); }
//    @Bean
//    public JobDetail workerJobDetail3() { return buildJobDetail(3); }
//    @Bean
//    public JobDetail workerJobDetail4() { return buildJobDetail(4); }
//    @Bean
//    public JobDetail workerJobDetail5() { return buildJobDetail(5); }
//
//    private JobDetail buildJobDetail(int workerId) {
//        return JobBuilder.newJob(QuartzJob.class)
//                .withIdentity("quartzJob-" + workerId, "batch") // JobKey
//                //.withDescription("worker " + workerId) // 설명
//                .usingJobData("workerId", workerId)
//                //.usingJobData(new JobDataMap(Map.of("k","v")))    // 여러개 한 번에
//                .storeDurably()
//                //.requestRecovery(true) // 실행중 다운되면 복구 시 재실행 시도
//                .build();
//    }
//
//    @Bean
//    public Trigger workerTrigger1(JobDetail workerJobDetail1) { return buildTrigger(workerJobDetail1, 1); }
//    @Bean
//    public Trigger workerTrigger2(JobDetail workerJobDetail2) { return buildTrigger(workerJobDetail2, 2); }
//    @Bean
//    public Trigger workerTrigger3(JobDetail workerJobDetail3) { return buildTrigger(workerJobDetail3, 3); }
//    @Bean
//    public Trigger workerTrigger4(JobDetail workerJobDetail4) { return buildTrigger(workerJobDetail4, 4); }
//    @Bean
//    public Trigger workerTrigger5(JobDetail workerJobDetail5) { return buildTrigger(workerJobDetail5, 5); }
//
//    private Trigger buildTrigger(JobDetail jobDetail, int workerId) {
//        return TriggerBuilder.newTrigger()
//                .forJob(jobDetail)
//                .withIdentity("workerTrigger-" + workerId, "batch")
//                .startNow()
//                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
//                        .withIntervalInSeconds(5)
//                        //.repeatForever()
//                        .withRepeatCount(3)
//                        .withMisfireHandlingInstructionNextWithRemainingCount()
//                )
//                .build();
//    }
//
///*
//    @Bean
//    public Trigger sampleEvery10SecTrigger(JobDetail quartzJobDetail) {
//        return TriggerBuilder.newTrigger()
//                .forJob(quartzJobDetail)
//                .withIdentity("sampleEvery10SecTrigger")
//                .startNow() //즉시 시작
////                .startAt(DateBuilder.futureDate(30, DateBuilder.IntervalUnit.SECOND)) //특정 시각에 시작 30초 후 시작
////                .endAt(DateBuilder.futureDate(10, DateBuilder.IntervalUnit.MINUTE))   //특정 시각에 종료 10분 후 종료
//                //픽스 딜레이방식
//                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
//                        .withIntervalInSeconds(10)//10초마다
//                        .repeatForever() // 무한 반복
//
//                        //.withRepeatCount(99) // 최초 1회 + 반복 99 = 총 100회
//                        .withMisfireHandlingInstructionNowWithExistingCount() // 밀린 거 “지금” 처리(카운트 유지)
//                        //.withMisfireHandlingInstructionNextWithRemainingCount() // 밀린 건 스킵하고 다음 텀부터
//                        //.withMisfireHandlingInstructionIgnoreMisfires() // 가능한 한 따라잡기(폭주 위험)
//                )
//                //크론 방식
//                .withSchedule(CronScheduleBuilder.cronSchedule("0 * * * * ?")
//                        .withMisfireHandlingInstructionDoNothing() // 놓친 건 버리고 다음부터
//                        //.withMisfireHandlingInstructionFireAndProceed() // 딱 1번 따라잡고 진행
//                        //.withMisfireHandlingInstructionIgnoreMisfires() // 따라잡기(주의)
//                 )
//
//                 //달력 기준으로 N분/시간/일/주/월마다” 같은 스케줄
//                .withSchedule(CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
//                      .withIntervalInDays(1)          // 매일
//                      // .withIntervalInWeeks(1)      // 매주
//                      // .withIntervalInMonths(1)     // 매월
//
//                      //한국은 DST 미사용으로 안해도됨
//                      .preserveHourOfDayAcrossDaylightSavings(true) // DST 영향 줄이기 매일/매주 같은 ‘시각(시/분)’에 실행되도록 최대한 유지
//                      .skipDayIfHourDoesNotExist(true)              // DST로 인해 “그 시각이 아예 존재하지 않는 날”이면 그 날은 스킵
//                )
//
//                //매일 특정 시간대에만” 반복하고 싶을 때 즉, 09:00~18:00 사이에서만 반복 실행
//                .withSchedule(dailyTimeIntervalSchedule()
//                      // 09:00~18:00 사이에서만 반복
//                      .startingDailyAt(hourAndMinuteOfDay(9, 0))
//                      .endingDailyAt(hourAndMinuteOfDay(18, 0))
//                      .withIntervalInMinutes(5) //위 시간대 안에서 5분 간격으로 실행
//
//                      //월~금만 작동하도록 요일제한
//                      .onDaysOfTheWeek(
//                            DateBuilder.MONDAY,
//                            DateBuilder.TUESDAY,
//                            DateBuilder.WEDNESDAY,
//                            DateBuilder.THURSDAY,
//                            DateBuilder.FRIDAY
//                        )
//                  )
//                //.withPriority(10) // 기본 5 여러 트리거가 동시에 발화할 때 우선순위 힌트를 줌.
//                .build();
//    }
//    */
//}
