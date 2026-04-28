# Maven Central 발행 가이드

## 0. 큰 그림

| 단계 | 어디서 | 무엇을 |
|---|---|---|
| 1 | Sonatype Central Portal | 계정 + namespace 검증 |
| 2 | 로컬 (GPG) | 서명용 PGP 키 생성 + 공개키 서버 업로드 |
| 3 | `~/.gradle/gradle.properties` | 토큰·서명키 시크릿 보관 |
| 4 | 본 레포 `gradle.properties` | `githubUser` 본인 ID로 변경 |
| 5 | 터미널 | `./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository` |

> Maven Central은 **누구나 등록할 수 있습니다.** 단 **groupId(=네임스페이스) 소유 증명**이 필요합니다.
> `io.github.<본인GitHub아이디>` namespace는 GitHub 계정만 있으면 자동 검증돼서 가장 쉽습니다.

---

## 1. Sonatype Central Portal 가입 + namespace 검증

1. https://central.sonatype.com 접속 → 우측 상단 **Sign In** → GitHub 로그인 권장
2. 로그인 후 **Namespaces** 메뉴 → **Add Namespace** → `io.github.<본인GitHub아이디>` 입력
3. GitHub 계정으로 가입했으면 **Verified** 자동 표시 (수 분 이내)
4. 사용자 토큰 발급
   - 우측 상단 프로필 → **View Account** → **Generate User Token**
   - `username` / `password` 두 줄을 받음 → 잘 보관

---

## 2. GPG 서명 키 만들기 (Maven Central은 서명 필수)

```bash
# 키 생성 (RSA 4096, 만료 0년 권장은 안 함 - 2~5년 정도)
gpg --full-generate-key

# 키 ID 확인
gpg --list-secret-keys --keyid-format=long
# → sec  rsa4096/ABCD1234EFGH5678 ...

# 공개키를 keyserver에 업로드 (Sonatype이 검증할 수 있도록 최소 1곳 필수)
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678
gpg --keyserver keys.openpgp.org --send-keys ABCD1234EFGH5678

# 비공개키를 ASCII 형식으로 export (gradle에 등록용)
gpg --export-secret-keys --armor ABCD1234EFGH5678 > /tmp/private.key
```

---

## 3. `~/.gradle/gradle.properties` 시크릿 등록

> ❗ **본 레포의 `gradle.properties`가 아니라 `~/.gradle/gradle.properties`** (홈 디렉토리). 절대 커밋하지 마세요.

```
ossrhUsername=<Sonatype Central User Token username>
ossrhPassword=<Sonatype Central User Token password>

# private.key 파일 내용을 한 줄로 만든 값 (개행은 \n으로 escape)
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----\n
signingPassword=<gpg key 만들 때 설정한 비밀번호>
```

private.key를 한 줄로 변환하는 가장 쉬운 방법:
```bash
awk '{printf "%s\\n", $0}' /tmp/private.key
```
출력값을 통째로 `signingKey=` 뒤에 붙여넣으세요.

---

## 4. 본 레포 `gradle.properties` 수정

```properties
githubUser=<본인GitHub아이디>
```

이 값으로 `group = io.github.<github아이디>` 가 자동 결정됩니다. POM의 url, scm, developer.id에도 같이 적용됩니다.

---

## 5. 발행

```bash
# 한 번에 staging → close → release 까지
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

성공하면 약 15~30분 후 https://repo1.maven.org/maven2/io/github/<본인id>/easy-quartz-spring-boot-starter/0.0.1/ 에 jar가 보입니다.
search.maven.org 검색 인덱스에는 1~4시간 정도 걸립니다.

문제 있을 때:
- `closeSonatypeStagingRepository` 단계에서 검증 실패 → POM 메타데이터(이름/설명/url/license/developer/scm) 누락 확인
- `signSonatypePublication` 실패 → `signingKey` 한 줄 변환 시 개행 누락
- 401 Unauthorized → 토큰 username/password 정확한지 확인 (계정 비밀번호 아님)

---

## 버전 올리기

`build.gradle` 루트의 `version = '0.0.1'`을 올리고 다시 발행. **이미 발행된 버전은 변경/삭제 불가** (Maven Central 정책).
SNAPSHOT 발행도 가능: `version = '0.0.2-SNAPSHOT'` → `central.sonatype.com/repository/maven-snapshots/`로 자동 이동.

---

## JitPack을 보조로 쓰기 (선택)

GitHub에 release tag만 만들면 끝. 별도 가입/서명 불필요.

사용자 입장:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.<본인GitHub아이디>.easy-quartz:easy-quartz-spring-boot-starter:0.0.1'
}
```

JitPack 첫 빌드는 약간 느릴 수 있습니다 (1~5분). 본 레포에 `jitpack.yml`이 이미 포함돼 있어 별도 설정 없이 동작합니다.
