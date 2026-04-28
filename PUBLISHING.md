# Maven Central 발행 가이드

본 문서는 `easy-quartz` 라이브러리를 Maven Central(Sonatype Central Portal)에 발행하기 위한 단계별 가이드입니다.

## 큰 그림

| 단계 | 위치 | 작업 |
| --- | --- | --- |
| 1 | https://central.sonatype.com | 계정 생성 및 namespace 검증 |
| 2 | 로컬 터미널 | GPG 서명용 PGP 키 생성과 공개키 서버 업로드 |
| 3 | `~/.gradle/gradle.properties` (홈) | 발행 토큰과 서명 키를 보관 |
| 4 | 본 레포의 `gradle.properties` | `githubUser` 값을 본인 GitHub 아이디로 변경 |
| 5 | 터미널 | `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository` |

> Maven Central은 누구나 등록할 수 있습니다. 다만 사용하는 `groupId`(=네임스페이스)에 대한 소유 증명이 필요합니다. `io.github.black-astro` 형식의 네임스페이스는 GitHub 계정 소유 사실만으로 자동 검증되므로 가장 빠르게 시작할 수 있습니다.

---

## 1. Sonatype Central Portal 가입과 namespace 검증

1. https://central.sonatype.com 에 접속한 뒤 우측 상단의 **Sign In**으로 로그인합니다. GitHub 계정 로그인을 권장합니다.
2. 로그인 후 **Namespaces** 메뉴에서 **Add Namespace**를 선택하고 `io.github.black-astro`를 입력합니다.
3. GitHub 계정으로 가입한 경우 수 분 이내에 자동으로 **Verified** 상태가 됩니다.
4. **Generate User Token**을 통해 발행용 토큰을 발급합니다. 토큰은 두 부분(`username`, `password`)으로 구성되며, **계정 비밀번호와는 다른 값**입니다.

> 토큰을 분실하거나 노출이 의심되는 경우, 같은 메뉴에서 새 토큰을 발급하면 이전 토큰은 자동으로 비활성화됩니다.

---

## 2. GPG 서명 키 준비

Maven Central은 모든 아티팩트(jar, pom, sources, javadoc)에 대한 PGP 서명을 요구합니다.

```bash
# 키 생성. 알고리즘은 RSA 4096, 만료는 2~5년 권장.
gpg --full-generate-key

# 키 ID 확인
gpg --list-secret-keys --keyid-format=long
# 출력 예: sec  rsa4096/ABCD1234EFGH5678  2026-04-28 [SC]

# 공개키를 keyserver에 업로드 (검증 대상이므로 최소 한 곳 이상 필수)
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678
gpg --keyserver keys.openpgp.org      --send-keys ABCD1234EFGH5678

# 비공개키를 ASCII 형식으로 추출 (Gradle에 전달할 용도)
gpg --export-secret-keys --armor ABCD1234EFGH5678 > ~/private.key
```

---

## 3. `~/.gradle/gradle.properties`에 시크릿 등록

> 본 레포의 `gradle.properties`가 아니라 **사용자 홈 디렉토리의 `~/.gradle/gradle.properties`** 입니다. 절대 커밋되어서는 안 됩니다.

```properties
# Sonatype Central Portal에서 발급받은 User Token
ossrhUsername=<token-username>
ossrhPassword=<token-password>

# 비공개키의 전체 본문을 한 줄로 변환한 값 (개행은 \n 으로 escape)
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----\n
signingPassword=<gpg key 생성 시 설정한 비밀번호>
```

비공개키를 한 줄로 변환하는 가장 간단한 방법은 다음과 같습니다.

```bash
awk '{printf "%s\\n", $0}' ~/private.key
```

출력 결과를 통째로 `signingKey=` 뒤에 붙여 넣습니다.

### 환경변수로 대체

CI 환경 등에서 `gradle.properties` 대신 환경변수를 쓸 수 있도록 본 프로젝트의 빌드 스크립트는 다음 변수를 함께 인식합니다.

| 환경변수 | 대응 프로퍼티 |
| --- | --- |
| `OSSRH_USERNAME` | `ossrhUsername` |
| `OSSRH_PASSWORD` | `ossrhPassword` |
| `SIGNING_KEY` | `signingKey` |
| `SIGNING_PASSWORD` | `signingPassword` |

---

## 4. 본 레포 `gradle.properties` 확인

현재 본 레포의 `gradle.properties`에는 다음 두 값이 채워져 있습니다.

```properties
githubUser=black-astro
githubRepo=easy-quartz
```

이 값들로부터 다음이 자동 결정됩니다.

- `group = io.github.${githubUser}` → `io.github.black-astro`
- POM `url`, `scm.connection`, `scm.developerConnection` → `https://github.com/black-astro/easy-quartz`
- POM `developer.id` → `${githubUser}`

> `group`은 GitHub 아이디만으로 결정되며 repo 이름과 무관합니다. 따라서 다른 repo로 옮기더라도 동일 좌표 `io.github.black-astro:easy-quartz-spring-boot-starter:...`로 발행할 수 있습니다.

---

## 5. 발행

```bash
# staging 업로드 → close → release 까지 한 번에 처리
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

성공하면 약 15~30분 후 `https://repo1.maven.org/maven2/io/github/black-astro/easy-quartz-spring-boot-starter/0.0.1/`에서 아티팩트를 확인할 수 있습니다. `search.maven.org`의 검색 인덱스에 반영되기까지는 추가로 1~4시간이 소요됩니다.

### 자주 만나는 문제

| 증상 | 원인과 해결 |
| --- | --- |
| `closeSonatypeStagingRepository`가 실패하며 검증 오류 발생 | POM 메타데이터(name, description, url, license, developer, scm) 누락. 본 프로젝트는 모두 채워져 있으므로 `githubUser` 미설정인지 우선 확인. |
| `signSonatypePublication` 실패 | `signingKey`를 한 줄로 변환할 때 개행이 누락된 경우가 가장 흔합니다. 위의 `awk` 명령으로 다시 변환하세요. |
| 401 Unauthorized | User Token의 username/password가 아닌 계정 비밀번호를 입력한 경우. Central Portal에서 다시 발급해 사용합니다. |

---

## 버전 관리

`build.gradle` 루트의 `version = '0.0.1'`을 올린 뒤 다시 발행합니다. **이미 발행된 버전은 변경하거나 삭제할 수 없습니다.** 이는 Maven Central의 정책입니다.

스냅샷 발행이 필요하다면 `version = '0.0.2-SNAPSHOT'`처럼 접미사를 부여합니다. 스냅샷은 `https://central.sonatype.com/repository/maven-snapshots/` 에 업로드되며, 정식 릴리스가 아닌 만큼 횟수 제한 없이 덮어쓸 수 있습니다.

---

## 부록 A. Maven 사용자가 토큰을 등록하는 방법 (`settings.xml`)

본 프로젝트는 Gradle 기반이므로 발행 시 `settings.xml`을 직접 다루지 않습니다. 다만 추후 다른 Maven 프로젝트에서 동일한 토큰으로 발행을 시도하는 경우, 또는 기존 Maven 빌드를 가지고 계신 경우를 대비해 형식을 안내합니다.

`~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
    <servers>
        <server>
            <id>central</id>
            <username>&lt;token-username&gt;</username>
            <password>&lt;token-password&gt;</password>
        </server>
    </servers>
</settings>
```

`<id>`는 `pom.xml`의 `<distributionManagement>`에서 참조하는 식별자와 일치해야 합니다. Sonatype Central Portal의 권장 식별자는 `central`입니다.

> Base64 인코딩된 `username:password` 문자열은 HTTP 인증 헤더용 형식입니다. `settings.xml`에는 그대로 username과 password를 분리해서 넣고, Base64 변환은 Maven이 알아서 수행합니다.

---

## 부록 B. JitPack을 보조 채널로 사용하기

JitPack은 GitHub 저장소를 스캔하여 즉석에서 아티팩트를 빌드해 배포해 주는 외부 서비스입니다. Maven Central 발행 절차가 부담스럽거나, 정식 릴리스 이전에 외부에 빠르게 공유해야 할 때 유용합니다.

라이브러리 사용자 입장의 좌표는 다음과 같습니다.

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.black-astro.easy-quartz:easy-quartz-spring-boot-starter:0.0.1'
}
```

본 레포에는 `jitpack.yml`이 포함되어 있어 별도 설정 없이도 동작합니다. 단 JitPack은 외부 서비스이므로 장기적인 가용성이 보장되지 않습니다. 안정적인 배포처가 필요하다면 Maven Central을 사용하시기 바랍니다.
