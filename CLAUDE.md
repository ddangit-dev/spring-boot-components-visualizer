# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Spring Boot 애플리케이션의 컴포넌트(빈), 인터페이스(MVC/WebSocket/JMS/Feign/JPA 등), 의존 관계를 PlantUML 스키마로 시각화하는 라이브러리. Spring REST Docs 처럼 사용자의 SpringBootTest 안에서 동작하면서 실행 중인 ApplicationContext를 검사하고, 추가로 BCEL 기반 bytecode 분석으로 호출 흐름을 따라간다.

- 산출물: `java-library` (`group=github.m4gshm`, `version=0.0.1-SNAPSHOT`)
- Java 11 (소스/타깃 모두), Spring Boot 2.7.18, Lombok 사용
- 멀티 프로젝트: 루트 모듈(라이브러리) + `:test:service1` (통합 테스트용 샘플 Spring Boot 앱)

## 자주 쓰는 명령

```bash
# 전체 빌드 (asciidoctor + pandoc 으로 README.md 재생성까지 수행)
./gradlew build

# pandoc 단계 건너뛰기 (pandoc 미설치 환경)
./gradlew build -Pno-pandoc

# 라이브러리 본체의 단위 테스트만
./gradlew :test

# service1 통합 테스트 (실제로 PlantUML/SVG 파일을 test/service1/src/schema/ 에 생성)
./gradlew :test:service1:test

# 단일 테스트 클래스 / 메서드
./gradlew :test:service1:test --tests "service1.SchemaGeneratorTest"
./gradlew :test:service1:test --tests "service1.SchemaGeneratorTest.generatePlantUml"

# Maven 로컬 publish (build.gradle.kts 에 정의된 GithubMavenRepo 로 publish)
./gradlew publish
```

`:test:service1:test` 는 build.gradle.kts 가 `PLANTUML_OUT` 환경변수를 자동으로 `$projectDir/src/schema` 로 세팅한다. 외부에서 직접 테스트를 돌릴 때는 같은 환경변수를 지정해야 `requireNonNull` 에서 죽지 않는다.

`./gradlew build` 는 기본적으로 `asciidoctor` → `pandoc` 작업을 거쳐 루트 `README.md` 를 재생성한다(`src/docs/asciidoc/readme.adoc` 가 원본). README 만 수동으로 고치면 다음 빌드에서 덮어쓰여지므로 `readme.adoc` 를 수정해야 한다.

## 아키텍처

### 라이브러리 진입점 두 개

사용자는 `@SpringBootTest` 컨텍스트 안에서 두 빈을 주입받아 쓴다. 둘 다 `src/main/resources/META-INF/spring.factories` 의 자동설정으로 등록된다(`autoconfigure/ComponentsExtractorAutoConfiguration`, `PlantUmlTextFactoryAutoConfiguration`).

- `ComponentsExtractor` — `ConfigurableApplicationContext` 에서 빈을 훑어 `Components` 모델(빈 + 인터페이스 + 의존)을 만든다. `Options.exclude` 로 패키지/타입/이름/predicate 단위 필터링, `failFast`, `stringifyLevel`, `includeUnusedOutInterfaces` 등을 제어.
- `PlantUmlTextFactory implements SchemaFactory<String>` — `Components` 를 PlantUML 텍스트로 변환. `Options` 로 컴포넌트/인터페이스 압축(`concatenateComponents`, `concatenateInterfaces`), 그룹화 방식 등을 조정. `SchemaFactory<T>` 가 일반화된 인터페이스이므로 다른 출력 포맷도 같은 자리에 끼워 넣을 수 있다.

### 패키지 레이아웃 (`io.github.m4gshm.components.visualizer`)

- `model/` — 도메인 모델: `Component`, `Interface` (Direction in/out/outIn, Type http/ws/jms/kafka/storage/scheduler 등), `Components`, `MethodId`, `CallPoint`, `HttpMethod`, `StorageEntity`, `Package` …
- `client/` — 인터페이스 종류별 추출 유틸: `JmsOperationsUtils`, `RestOperationsUtils`, `WebsocketClientUtils`, `SchedulingConfigurerUtils`. 새 프로토콜 지원을 추가할 때 보통 여기에 새 Utils 가 들어오고, `ComponentsExtractor` 가 이를 호출한다.
- `eval/bytecode/` — BCEL 기반 bytecode evaluator. `Eval` 이 메서드 코드를 흉내내며 인스트럭션을 추적하고, `EvalContextFactory(Cache)Impl`, `InvokeBranch`, `InstructionUtils`, `LocalVariableUtils`, `InvokeDynamicUtils`, `ArithmeticUtils`, `StringifyResolver` 등이 보조한다. `JmsTemplate.send(...)` 같은 동적 호출에서 실제 큐 이름 같은 인자를 정적으로 끌어내는 게 목적.
- `eval/result/` — bytecode 평가 결과 타입: `Result`(루트), `Constant`, `Variable`, `Multiple`, `Delay(Invoke|LoadFromStore)`, `Stub`, `Illegal`, `Duplicate`, `Resolver`. `Resolver` 가 `Delay*` 결과를 풀어서 `Constant`/`Multiple`/`Variable` 로 좁힌다.
- `autoconfigure/` — Spring Boot 자동설정 두 개.
- 최상위 헬퍼: `ComponentsExtractorUtils`, `PlantUmlTextFactoryUtils`, `CallPointsHelper`, `UriUtils`, `Utils`, `IndentStringAppender`, `StubFactory(Impl)`, `LambdaProxyClassesDumper`.

### 데이터 흐름

`ComponentsExtractor.getComponents()` 호출 시:
1. `ConfigurableListableBeanFactory` 에서 빈을 가져와 옵션 필터를 적용.
2. 각 빈에 대해 클래스 어노테이션/타입 검사 (`@Controller`, `@FeignClient`, `WebSocketHandler`, `Repository`, `JmsOperations`, `RestOperations`, …) 로 인터페이스 후보를 만듦.
3. `eval/bytecode` 의 `Eval` 가 클라이언트 호출 (`JmsTemplate`, `RestTemplate`, `WebSocketClient` 등) 의 인자를 풀어 out 인터페이스의 실제 destination 을 결정.
4. `CallPointsHelper`/`EvalContextFactoryImpl.getCallPoints` 로 인터페이스 호출 지점이 실제 코드에서 도달 가능한지 검사하여 (`includeUnusedOutInterfaces=false` 일 때) 미사용 out 인터페이스 제거.
5. 의존 관계 + 컴포넌트 + 인터페이스를 `Components` 로 묶어 반환.

`PlantUmlTextFactory.create(components)` 가 `Components` 를 PlantUML 텍스트로 직렬화. 사용자는 결과를 파일로 쓰고, 보통 `com.plantuml.api.cheerpj.v1.Svg.convert(...)` 로 SVG 까지 만든다(`SchemaGeneratorTest` 의 `writeSwgFile` 패턴).

### `:test:service1` 의 위상

`test/service1` 는 단순 fixture 가 아니라 실제로 빌드되는 Spring Boot 애플리케이션이다(`build.gradle.kts` 가 `org.springframework.boot` 플러그인을 적용). MVC 컨트롤러, WebSocket 서비스, JMS 리스너/클라이언트, Feign 클라이언트, JPA + MongoDB 리포지토리, `@Scheduled` 빈 등 라이브러리가 지원하는 모든 인터페이스 종류를 한 곳에 모아둔 실제 환경이다. 테스트가 곧 데모이고, `src/schema/components.{puml,svg}` 가 README 의 예시 그림이다. 새로운 인터페이스 종류를 지원하게 되면 `service1` 에 사용 예제를 추가하고 스키마를 재생성하는 게 정석.

`SchemaGeneratorMockReposTest` 는 `io.github.m4gshm:spring-data-auto-mock` 의 `@ReplaceRepositoriesByMocks` 로 DataSource 없이 리포지토리만 mock 하는 패턴 — DB 없는 환경에서 스키마 생성을 검증할 때 참고.

## 코딩 메모

- Lombok (`@Data`, `@Builder(toBuilder=true)`, `@FieldDefaults(level=PRIVATE, makeFinal=true)`, `@Slf4j`) 가 광범위하게 쓰인다. 새 모델 클래스도 같은 패턴을 따른다.
- Spring Web/WebSocket/JMS/Data 는 모두 `compileOnly` — 라이브러리 사용자가 가져오는 환경 의존성에 맞춰 동적으로 처리해야 한다. `ComponentsExtractor` 의 `static { loadedClass(() -> ...) }` 패턴처럼, 클래스 로딩 실패에 안전하게 분기해야 한다.
- bytecode 분석은 BCEL (`org.apache.bcel:bcel`) 을 직접 다룬다. `eval/bytecode` 코드를 고칠 때는 JVM 인스트럭션 시맨틱을 깨뜨리지 않게 주의.
- 라이브러리는 사용자 코드의 실제 컴파일된 `.class` 를 읽기 때문에, 테스트는 반드시 컴파일이 끝난 상태에서 돈다(`./gradlew :test:service1:build` 후 `:test`).
