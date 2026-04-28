# easy-quartz

선언적 어노테이션 한 개로 스케줄을 등록하기 위한 Spring Boot Starter입니다.

`@EasyQuartzScheduled` 한 개로 다섯 가지 스케줄링 패턴(CRON, FIXED_RATE, FIXED_DELAY, CALENDAR, DAILY_TIME)을 표현할 수 있으며, `engine` 속성으로 **Quartz Scheduler**와 **Spring TaskScheduler** 중 어디서 실행할지 선택할 수 있습니다.

```java
@EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 9 * * ?")
public void everyMorning() {
    // 매일 09:00 실행 (기본 엔진은 Quartz)
}
```

---

## 목차

- [전제 조건](#전제-조건)
- [설치](#설치)
- [활성화](#활성화)
- [한눈에 보는 사용법](#한눈에-보는-사용법)
- [실행 엔진 선택: Quartz와 Spring](#실행-엔진-선택-quartz와-spring)
- [스케줄 타입](#스케줄-타입)
- [전체 옵션 레퍼런스](#전체-옵션-레퍼런스)
- [실전 시나리오](#실전-시나리오)
- [구성 (`application.yml`)](#구성-applicationyml)
- [동작 원리](#동작-원리)
- [성능과 운영](#성능과-운영)
- [라이브러리 빌드 및 발행](#라이브러리-빌드-및-발행)
- [라이선스](#라이선스)

---

## 전제 조건

- **Java 21** 이상
- **Spring Boot 3.x** (3.4.5에서 검증되었습니다)

starter 의존성을 추가하면 Quartz, AOP, Validation은 자동으로 따라옵니다.

---

## 설치

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.black-astro:easy-quartz-spring-boot-starter:0.0.1'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.black-astro:easy-quartz-spring-boot-starter:0.0.1")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.black-astro</groupId>
    <artifactId>easy-quartz-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

> Maven Central에서 받는 경우 별도 repository 설정이 필요하지 않습니다.

---

## 활성화

`@SpringBootApplication`이 붙은 메인 클래스 또는 임의의 `@Configuration` 클래스에 `@EnableEasyQuartz`를 한 번만 부착합니다.

```java
import com.gibis.easyquartz.interfaces.EnableEasyQuartz;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableEasyQuartz
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

이 어노테이션이 적용되면 다음이 자동으로 활성화됩니다.

- `EasyQuartzAutoConfiguration` (Quartz 등록기, Spring 등록기, 자체 TaskScheduler)
- `application.yml`의 `easy.quartz.*` 프로퍼티 바인딩
- `@EasyQuartzScheduled`이 부착된 빈 메서드를 자동 스캔

---

## 한눈에 보는 사용법

스케줄을 실행할 메서드는 반드시 Spring 빈에 속해야 하며, **public·인자 0개·반환 타입 void**여야 합니다.

```java
import com.gibis.easyquartz.enums.*;
import com.gibis.easyquartz.interfaces.EasyQuartzScheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportJobs {

    /* 1. CRON: 표준 cron 표현식 */
    @EasyQuartzScheduled(
        name = "daily-report",
        group = "report",
        type = ScheduleType.CRON,
        cron = "0 0 9 * * ?",
        timeZone = "Asia/Seoul",
        misfire = MisfirePolicy.FIRE_AND_PROCEED,
        priority = 7,
        description = "매일 09:00 일일 리포트 생성"
    )
    public void dailyReport() { }

    /* 2. FIXED_RATE: 시작 시점 기준 5분 간격, 최대 10회 실행 */
    @EasyQuartzScheduled(
        type = ScheduleType.FIXED_RATE,
        fixedRateMinutes = 5,
        repeatForever = false,
        repeatCount = 9,
        startDelaySeconds = 30,
        jitterSeconds = 5
    )
    public void warmupPing() { }

    /* 3. FIXED_DELAY: 이전 실행 완료 후 3분 뒤 재실행. 동시 실행 불가 */
    @EasyQuartzScheduled(
        type = ScheduleType.FIXED_DELAY,
        fixedDelayMinutes = 3,
        disallowConcurrent = true,
        endAfterSeconds = 3600,
        jobData = { "queueName=primary", "batchSize=200" }
    )
    public void drainQueue() { }

    /* 4. CALENDAR: 격주 월요일 단위 (DST 안전) */
    @EasyQuartzScheduled(
        type = ScheduleType.CALENDAR,
        calendarUnit = CalendarUnit.WEEKS,
        calendarInterval = 2,
        preserveHourAcrossDst = true,
        skipDayIfHourDoesNotExist = true
    )
    public void biweekly() { }

    /* 5. DAILY_TIME: 평일 09:00~18:00, 30분마다 */
    @EasyQuartzScheduled(
        type = ScheduleType.DAILY_TIME,
        dailyStartHour = 9,  dailyStartMin = 0,
        dailyEndHour   = 18, dailyEndMin   = 0,
        intervalMinutes = 30,
        daysOfWeek = { Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI }
    )
    public void onlyDuringWorkHours() { }

    /* 6. Spring 엔진: Quartz를 거치지 않고 Spring TaskScheduler에서 실행 */
    @EasyQuartzScheduled(
        engine = SchedulerEngine.SPRING,
        type = ScheduleType.CRON,
        cron = "*/10 * * * * *"
    )
    public void everyTenSecondsOnSpring() { }
}
```

---

## 실행 엔진 선택: Quartz와 Spring

`engine` 속성으로 동일한 어노테이션을 두 가지 실행 엔진 중 하나에 위임할 수 있습니다.

| 항목 | `SchedulerEngine.QUARTZ` (기본값) | `SchedulerEngine.SPRING` |
| --- | --- | --- |
| 지원 타입 | CRON, FIXED_RATE, FIXED_DELAY, CALENDAR, DAILY_TIME | CRON, FIXED_RATE, FIXED_DELAY |
| misfire 정책 | 지원 (`DO_NOTHING` / `FIRE_AND_PROCEED` / `IGNORE_MISFIRES`) | 미지원 |
| 트리거 우선순위 (`priority`) | 지원 | 미지원 |
| 영속화 (JDBC JobStore) | 지원 | 미지원 |
| 클러스터링 | 지원 | 미지원 |
| `requestRecovery` | 지원 | 미지원 |
| `jobData` 전달 | `JobDataMap`으로 전달 | 등록 메타정보로만 사용 |
| 외부 의존성 | Quartz 필요 | Quartz 사용하지 않음 |
| 적합한 용도 | 운영 배치, 분산 환경, 영속/복구 필요 | 단일 인스턴스 워밍업, 헬스체크, 가벼운 인프라 잡 |

> 한 애플리케이션 안에서 메서드별로 엔진을 자유롭게 섞을 수 있습니다. 예를 들어 무거운 배치는 Quartz, 5초 헬스 핑은 Spring 식으로 사용할 수 있습니다.

### Spring 엔진의 TaskScheduler

Spring 엔진은 라이브러리가 자체적으로 등록하는 `easyQuartzSpringTaskScheduler`(스레드 풀 4개)를 기본 사용합니다. 사용자가 `TaskScheduler` 빈을 별도로 등록한 경우에는 그것을 우선 사용합니다. `@EnableScheduling`으로 등록된 Spring 자체 스케줄러와는 격리되므로 기존 `@Scheduled`와 간섭하지 않습니다.

스레드 풀 크기를 늘리고 싶다면 사용자 설정에서 직접 빈을 등록하면 됩니다.

```java
@Configuration
public class SchedulerConfig {

    @Bean(name = "easyQuartzSpringTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler customTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setThreadNamePrefix("easy-quartz-spring-");
        s.setPoolSize(16);
        s.initialize();
        return s;
    }
}
```

---

## 스케줄 타입

### CRON

표준 6필드(초·분·시·일·월·요일) Quartz cron 표현식입니다. Spring 엔진에서도 동일한 6필드 표현식이 사용됩니다.

```java
@EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 */15 * * * ?")
public void everyFifteenMinutes() { }
```

### FIXED_RATE

이전 실행이 **시작된 시각**을 기준으로 일정 간격마다 실행합니다. `fixedRateHours`, `fixedRateMinutes`, `fixedRateSeconds` 중 하나 이상을 지정합니다.

```java
@EasyQuartzScheduled(type = ScheduleType.FIXED_RATE, fixedRateMinutes = 5)
public void everyFiveMinutes() { }
```

`repeatForever = false`로 두면 `repeatCount`로 횟수를 제한할 수 있습니다.

### FIXED_DELAY

이전 실행이 **종료된 시각**을 기준으로 일정 시간 후 다시 실행합니다. 이전 실행과 다음 실행이 절대 겹치지 않습니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.FIXED_DELAY,
    fixedDelayMinutes = 10
)
public void tenMinutesAfterPrevious() { }
```

> Quartz 엔진의 FIXED_DELAY는 안전을 위해 항상 `disallowConcurrent = true`를 요구합니다.

### CALENDAR

일·주·월 단위 반복에 사용합니다. 한 달, 격주처럼 사람이 이해하기 쉬운 단위로 표현하기 위한 타입이며 DST(일광절약시간) 처리를 직접 제어할 수 있습니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.CALENDAR,
    calendarUnit = CalendarUnit.MONTHS,
    calendarInterval = 1,
    preserveHourAcrossDst = true,
    skipDayIfHourDoesNotExist = true
)
public void monthly() { }
```

> CALENDAR 타입은 Quartz 엔진에서만 지원됩니다.

### DAILY_TIME

매일 특정 시간대 안에서 일정 간격으로 반복합니다. 업무 시간 동안만 동작하는 잡, 특정 요일에만 동작하는 잡에 적합합니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.DAILY_TIME,
    dailyStartHour = 9,  dailyStartMin = 0,
    dailyEndHour   = 18, dailyEndMin   = 0,
    intervalSeconds = 30,
    daysOfWeek = { Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI }
)
public void everyHalfMinuteDuringWorkHours() { }
```

> DAILY_TIME 타입은 Quartz 엔진에서만 지원됩니다.

---

## 전체 옵션 레퍼런스

`@EasyQuartzScheduled`의 모든 속성을 정리합니다.

### 식별

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `name` | `String` | `""` | 잡/트리거 이름. 비우면 `Bean이름.메서드이름`으로 자동 결정. |
| `group` | `String` | `"DEFAULT"` | 잡 그룹. 같은 group 안에서는 `name`이 유일해야 합니다. |
| `description` | `String` | `""` | Quartz `JobDetail.description`에 그대로 전달. |

### 타입 선택 (필수)

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `type` | `ScheduleType` | (필수) | `CRON`, `FIXED_RATE`, `FIXED_DELAY`, `CALENDAR`, `DAILY_TIME` 중 선택. |
| `engine` | `SchedulerEngine` | `QUARTZ` | `QUARTZ` 또는 `SPRING`. |

### CRON 전용

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `cron` | `String` | `""` | Quartz cron 표현식. `type=CRON`일 때 필수. |

### FIXED_RATE / FIXED_DELAY 전용

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `fixedRateHours` / `fixedRateMinutes` / `fixedRateSeconds` | `long` | `0` | FIXED_RATE 간격. 합계가 양수여야 함. |
| `fixedDelayHours` / `fixedDelayMinutes` / `fixedDelaySeconds` | `long` | `0` | FIXED_DELAY 간격. 합계가 양수여야 함. |
| `repeatForever` | `boolean` | `true` | FIXED_RATE만 적용. 무한 반복 여부. |
| `repeatCount` | `int` | `-1` | `repeatForever=false`일 때 반복 횟수. |

### CALENDAR 전용

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `calendarUnit` | `CalendarUnit` | `NONE` | `DAYS`, `WEEKS`, `MONTHS` 중 선택. |
| `calendarInterval` | `int` | `1` | 단위 간격. `calendarUnit=WEEKS, calendarInterval=2`이면 격주. |
| `preserveHourAcrossDst` | `boolean` | `true` | DST 변경 시 시간대를 유지. |
| `skipDayIfHourDoesNotExist` | `boolean` | `true` | DST로 존재하지 않는 시간이면 그날을 건너뜀. |

### DAILY_TIME 전용

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `dailyStartHour`, `dailyStartMin` | `int` | `-1`, `0` | 매일 시작 시각. 시는 0~23. |
| `dailyEndHour`, `dailyEndMin` | `int` | `-1`, `0` | 매일 종료 시각. |
| `intervalHours` / `intervalMinutes` / `intervalSeconds` | `int` | `0` | 발화 간격. 초가 60 이상이면 분 단위로 등록됩니다. |
| `daysOfWeek` | `Day[]` | `{}` | 동작할 요일. 비우면 매일. |

### 시작과 종료

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `startDelaySeconds` | `long` | `0` | 첫 실행을 지연시킬 시간(초). |
| `endAfterSeconds` | `long` | `-1` | 등록 시점으로부터 N초 뒤 자동 종료. `-1`이면 무제한. |
| `jitterSeconds` | `long` | `0` | 첫 실행 시각에 0~N초 사이의 임의 지연을 더합니다. 다수 잡의 동시 발화를 분산할 때 유용. |

### 타임존과 misfire

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `timeZone` | `String` | `""` | IANA 타임존. 비우면 `easy.quartz.default-time-zone` 값 사용. |
| `misfire` | `MisfirePolicy` | `DO_NOTHING` | `DO_NOTHING` / `FIRE_AND_PROCEED` / `IGNORE_MISFIRES`. Quartz 엔진 전용. |

### Quartz 고급 옵션

| 옵션 | 타입 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `priority` | `int` | `5` | 동일 시각에 다수 트리거가 경합할 때 큰 값이 우선. |
| `requestRecovery` | `boolean` | `false` | JDBC JobStore + 클러스터링 환경에서 비정상 종료된 실행을 다음 기동 시 복구. |
| `disallowConcurrent` | `boolean` | `true` | 이전 실행이 끝나기 전에는 다음 실행 시작 안 함. FIXED_DELAY는 강제로 `true`. |
| `jobData` | `String[]` | `{}` | `"key=value"` 형식. `JobDataMap`으로 전달되어 `JobExecutionContext`에서 조회 가능. |

---

## 실전 시나리오

### 1. JobDataMap으로 잡에 파라미터 전달하기

`@Component` 메서드에 `@EasyQuartzScheduled`를 부착할 때 `jobData`로 임의의 키-값을 전달할 수 있습니다. Quartz 엔진은 이를 `JobDataMap`에 저장합니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.FIXED_RATE,
    fixedRateMinutes = 1,
    jobData = { "target=USER", "limit=500" }
)
public void crawl() {
    // 메서드 본문에서 직접 JobDataMap을 조회하려면
    // 별도 Quartz Job 클래스를 직접 작성하거나, 빈에 환경값/프로퍼티를 주입해 사용합니다.
}
```

> 참고: `jobData`는 잡 식별과 모니터링(예: 동일 메서드를 파라미터만 다르게 여러 번 등록하는 경우)에서 가치가 큽니다. 메서드 호출 자체에 인자를 전달하지는 않습니다.

### 2. jitter로 부하 분산하기

여러 인스턴스가 동시에 외부 API를 호출하지 않도록 시작 시각을 분산시킵니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.FIXED_RATE,
    fixedRateMinutes = 5,
    jitterSeconds = 60   // 첫 실행을 0~60초 사이 임의 지연
)
public void callExternalApi() { }
```

### 3. 트리거 우선순위로 동시 발화 시 순서 제어

```java
@EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 0 * * ?", priority = 10)
public void criticalMidnightJob() { }

@EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 0 * * ?", priority = 1)
public void lowPriorityMidnightJob() { }
```

자정에 두 트리거가 동시에 발화되면 Quartz는 `priority`가 큰 트리거를 먼저 가져갑니다.

### 4. 클러스터링 환경에서 안전 복구 켜기

JDBC JobStore + 클러스터링 환경이라면, 잡 실행 중 노드가 죽었을 때 다른 노드에서 복구되도록 `requestRecovery`를 켭니다.

```java
@EasyQuartzScheduled(
    type = ScheduleType.CRON,
    cron = "0 0 * * * ?",
    requestRecovery = true
)
public void importantHourly() { }
```

> 멱등하지 않은 잡(예: 결제 처리)에는 신중히 사용하시기 바랍니다.

### 5. 기존 `@Scheduled`에서 `@EasyQuartzScheduled`로 전환

```java
// 변경 전 (Spring @Scheduled)
@Scheduled(cron = "0 */10 * * * *")
public void poll() { }

// 변경 후 (Spring 엔진 그대로 사용)
@EasyQuartzScheduled(
    engine = SchedulerEngine.SPRING,
    type = ScheduleType.CRON,
    cron = "0 */10 * * * *"
)
public void poll() { }
```

`@EnableScheduling`을 켜지 않아도 동작합니다. Quartz로 옮길 준비가 되면 `engine` 한 줄만 지우거나 `QUARTZ`로 바꾸면 됩니다.

---

## 구성 (`application.yml`)

라이브러리 전역 동작은 `application.yml` 또는 `application.properties`에서 조정합니다.

```yaml
easy:
  quartz:
    enabled: true
    update-existing: true
    default-time-zone: Asia/Seoul
    scan-packages:
      - com.example.jobs
      - com.example.reports
```

| 프로퍼티 | 기본값 | 설명 |
| --- | --- | --- |
| `easy.quartz.enabled` | `true` | 라이브러리 전체 활성화 여부. |
| `easy.quartz.update-existing` | `true` | 재시작 시 동일한 키의 기존 스케줄을 새 정의로 덮어씀. JDBC JobStore에서 의미가 큽니다. |
| `easy.quartz.default-time-zone` | `Asia/Seoul` | 어노테이션의 `timeZone`이 비어 있을 때 사용. |
| `easy.quartz.scan-packages` | `[]` | 잡을 탐색할 패키지 목록. 비어 있으면 전체 빈 대상으로 스캔. |

> Quartz 자체의 `spring.quartz.*` 설정도 그대로 사용 가능합니다. JDBC JobStore, 클러스터링, 스레드 풀 크기 등은 Spring Boot 공식 가이드를 따릅니다.

---

## 동작 원리

1. `@EnableEasyQuartz`가 `EasyQuartzAutoConfiguration`을 import합니다.
2. AutoConfiguration이 다음 빈을 등록합니다.
   - `EasyQuartzBackwardRegistrar` — 레거시 단일 어노테이션(`@EasyQuartzCron` 등) 처리.
   - `EasySpringRegistrar` — Spring 엔진 등록 처리.
   - `EasyQuartzRegistrar` — `@EasyQuartzScheduled` 진입점. 엔진/타입 분기.
   - `easyQuartzSpringTaskScheduler` — Spring 엔진 전용 `ThreadPoolTaskScheduler`(스레드 4개).
3. `EasyQuartzRegistrar`는 `SmartInitializingSingleton`이며, 컨텍스트 초기화 직후 다음을 수행합니다.
   - 모든 빈 정의를 순회하며 `scan-packages` 필터를 적용합니다.
   - 메서드별로 `@EasyQuartzScheduled`를 찾아 `engine`에 따라 분기합니다.
   - QUARTZ면 `JobDetail`/`Trigger`를 만들어 `Scheduler.scheduleJob`(또는 `rescheduleJob`)로 등록합니다.
   - SPRING이면 `TaskScheduler.schedule(...)`로 직접 등록합니다.
4. 실제 실행 시 Quartz `EasyQuartzMethodJob`은 `SchedulerContext`에 저장된 `ApplicationContext`로부터 빈을 조회하고, 캐시된 `Method`로 메서드를 호출합니다.

---

## 성능과 운영

### 실행 경로의 reflection 비용

`EasyQuartzMethodJob`은 매 실행마다 메서드를 다시 lookup하지 않습니다. 첫 실행 시 한 번만 `Class.getMethod`로 가져온 뒤 정적 `ConcurrentHashMap`에 캐시합니다. 결과적으로 잡 실행 핫 패스의 reflection 오버헤드는 한 번의 `Method.invoke()`만 남습니다.

### 부팅 시 빈 인스턴스화

라이브러리는 `ApplicationContext.getBean(beanName)`을 사용해 빈을 순회합니다. 이는 `@Lazy` 빈도 부팅 시점에 인스턴스화함을 뜻합니다. 빌드 시간이 아니라 부팅 시간에만 영향을 미치며, 기동 후에는 추가 비용이 없습니다.

### 동시성과 throughput

- Quartz의 throughput은 `spring.quartz.properties.org.quartz.threadPool.threadCount`로 제어합니다. 기본값 10이 부족하다면 늘리세요.
- Spring 엔진의 throughput은 `easyQuartzSpringTaskScheduler`의 풀 크기로 제어합니다. 기본값 4를 늘리려면 [실행 엔진 선택](#실행-엔진-선택-quartz와-spring) 섹션의 빈 재정의 예시를 참고하세요.

### 운영 팁

- **JDBC JobStore + 클러스터링**: 잡 영속화와 다중 노드 안전 실행이 필요하면 `spring.quartz.job-store-type=jdbc`와 `org.quartz.jobStore.isClustered=true`로 설정하세요. 이 모드에서는 `requestRecovery`, `update-existing` 옵션이 의미를 가집니다.
- **그레이스풀 셧다운**: 본 라이브러리가 자체 등록한 `easyQuartzSpringTaskScheduler`는 컨테이너 종료 시 30초까지 진행 중 작업을 기다립니다. 더 긴 시간이 필요하면 빈을 재정의하세요.
- **모니터링**: Quartz는 `JobListener`/`TriggerListener`를 통해 실행 통계를 수집할 수 있습니다. 직접 빈으로 등록하면 `Scheduler`가 자동으로 인식합니다.

---

## 라이브러리 빌드 및 발행

### 로컬 빌드

```bash
./gradlew build -x test                     # 라이브러리 빌드
./gradlew publishToMavenLocal               # ~/.m2 로컬 캐시에 publish
./gradlew build -PwithSample=true           # 샘플 앱 포함 빌드
```

### Maven Central 발행

발행 절차의 단계별 가이드는 [PUBLISHING.md](PUBLISHING.md)를 참고하시기 바랍니다.

---

## 라이선스

Apache License 2.0
