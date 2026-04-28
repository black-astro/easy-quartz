# easy-quartz

선언적 어노테이션 한 개로 Quartz 스케줄을 등록하는 Spring Boot starter.

```java
@EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 * * * ?")
public void hourlyTask() {
    // 매시 정각에 실행
}
```

`@Scheduled` 처럼 쓰지만, 뒤에서는 Quartz가 돌아 클러스터링·misfire 정책·영속화를 그대로 활용할 수 있습니다.

---

## 요구사항

- Java **21+**
- Spring Boot **3.x** (테스트는 3.4.5 기준)

---

## 설치

### Gradle (Kotlin/Groovy)
```gradle
dependencies {
    implementation 'io.github.<github아이디>:easy-quartz-spring-boot-starter:0.0.1'
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.&lt;github아이디&gt;</groupId>
    <artifactId>easy-quartz-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

> starter만 추가하면 `easy-quartz-spring-boot-autoconfigure`, `spring-boot-starter-quartz`, `spring-boot-starter-aop`, `spring-boot-starter-validation`이 transitive로 따라옵니다.

---

## 빠른 시작 (3단계)

### 1) `@EnableEasyQuartz` 활성화
`@SpringBootApplication`이 붙은 메인 클래스나 `@Configuration` 클래스에 한 번만 추가합니다.

```java
@SpringBootApplication
@EnableEasyQuartz
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

### 2) 메서드에 `@EasyQuartzScheduled` 부착
```java
@Component
public class ReportJobs {

    @EasyQuartzScheduled(type = ScheduleType.CRON, cron = "0 0 9 * * ?")
    public void everyMorning() {
        // 매일 09:00
    }

    @EasyQuartzScheduled(type = ScheduleType.FIXED_RATE, fixedRateMinutes = 5)
    public void everyFiveMinutes() {
        // 5분마다 (이전 실행 시작 시점 기준)
    }

    @EasyQuartzScheduled(
        type = ScheduleType.DAILY_TIME,
        dailyStartHour = 9, dailyEndHour = 18,
        intervalMinutes = 30,
        daysOfWeek = {Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI}
    )
    public void onlyDuringWorkHours() {
        // 평일 09:00~18:00, 30분마다
    }
}
```

### 3) (선택) `application.yml` 설정
```yaml
easy:
  quartz:
    enabled: true              # 기본값: true
    update-existing: true      # 재시작 시 기존 스케줄 갱신
    default-time-zone: Asia/Seoul
    scan-packages:             # 비우면 전체 스캔
      - com.example.jobs
```

---

## 스케줄 타입 (`ScheduleType`)

| 타입 | 설명 | 주요 옵션 |
|---|---|---|
| `CRON` | cron 표현식 | `cron` |
| `FIXED_RATE` | N시간/분/초마다 (시작 시점 기준) | `fixedRateHours`, `fixedRateMinutes`, `fixedRateSeconds` |
| `FIXED_DELAY` | 이전 종료 후 N 뒤 재실행 | `fixedDelayHours`, `fixedDelayMinutes`, `fixedDelaySeconds` |
| `CALENDAR` | 일/주/월 단위 반복 | `calendarUnit`, `calendarInterval` |
| `DAILY_TIME` | 매일 특정 시간대 반복 | `dailyStartHour`, `dailyEndHour`, `intervalMinutes`, `daysOfWeek` |

### 자주 쓰는 공통 옵션

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `name` | (자동) | 스케줄 이름 (비우면 `Bean이름.메서드이름`) |
| `group` | `DEFAULT` | 그룹 이름 |
| `disallowConcurrent` | `true` | 이전 실행이 끝나야 다음 실행 |
| `startDelaySeconds` | `0` | 앱 시작 후 N초 뒤 첫 실행 |
| `endAfterSeconds` | `-1` | 시작 후 N초 뒤 자동 종료 (`-1`이면 무제한) |
| `timeZone` | (전역설정) | `Asia/Seoul`, `UTC` 등 |
| `misfire` | `DO_NOTHING` | `DO_NOTHING` / `FIRE_AND_PROCEED` / `IGNORE_MISFIRES` |

전체 옵션은 [`EasyQuartzScheduled`](easy-quartz-spring-boot-autoconfigure/src/main/java/com/gibis/easyquartz/interfaces/EasyQuartzScheduled.java) 참고.

---

## 빌드 (라이브러리 개발자용)

```bash
# 빌드 (테스트 제외)
./gradlew build -x test

# 로컬 Maven 캐시에 publish (개발 중 다른 프로젝트에서 가져다 쓸 때)
./gradlew publishToMavenLocal

# 샘플 앱 포함하여 빌드
./gradlew build -PwithSample=true
```

---

## Maven Central 발행

자세한 사용자 액션은 [PUBLISHING.md](PUBLISHING.md) 참고. 요약:

1. [Sonatype Central Portal](https://central.sonatype.com) 가입
2. namespace `io.github.<본인GitHub아이디>` 자동 검증 받기
3. GPG 키 생성 후 Central에 시크릿 등록
4. `~/.gradle/gradle.properties`에 토큰/서명키 등록
5. `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository`

---

## 라이선스

Apache License 2.0
