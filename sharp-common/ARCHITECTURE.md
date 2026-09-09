# sharp-common 架构说明

> 本文解释"影响使用方式"的架构事实，均从源码/构建脚本核实。API 细节见 [API.md](./API.md)。

## 1. 在依赖链中的位置

```
sharp-common  ←  sharp-database  ←  { sharp-meta, sharp-fileupload }  ←  sharp-formflow
     ↑                ↑
  业务工程（如 sharp-test）通常经 sharp-database 间接获得（database 的 build.gradle: api project(':sharp-common')）
```

- 本仓库中 sharp-common 是**唯一的最底层模块**，不依赖任何 sharp 兄弟模块。
- 它反向渗透进上层：sharp-database 的 DAO 条件 API 以 `SFunction` 为参数类型、其自动配置注册 common 的 `ValidatorHelper` Bean 和 http/convert 转换器；sharp-fileupload / sharp-formflow 使用 `IdGenerator`。
- 版本坐标：`com.rick.common:sharp-common:${libs.versions.sharp}`（当前 0.0.1-SNAPSHOT），发布到 mavenLocal。

## 2. 包职责（一句话一个包）

| 包 | 职责 |
|---|---|
| `constant` | 文件扩展名/MIME 相关常量（`FileConstants`） |
| `function` | 可序列化 Lambda 元信息（`SFunction`/`SConsumer`/`SInfo`，反解属性名） |
| `http.model` | 统一返回结构 `Result`/`ResultCode`/`ResultUtils` |
| `http.exception` | 业务异常 `BizException`、错误码接口 `ExceptionCode`、全局 `@RestControllerAdvice` |
| `http.web` | Web MVC 装配：`SharpWebMvcConfigurer`（被业务继承）+ 返回值自动包装 handler |
| `http.web.config` | `@EnableResultWrapped` 开关及其 `@Import` 的装配类 |
| `http.web.annotation` | `@UnWrapped`（方法级退出包装） |
| `http.web.param` | `@ParamName` 查询参数别名绑定（Processor + DataBinder） |
| `http.convert` | Spring `Converter`/`ConverterFactory`/`ConditionalGenericConverter`：String→枚举(code)、String→LocalDate、JSON String→Map/List/Set/Collection/JsonValue 对象、LocalDateTime→Instant |
| `http.json.serializer` | 实体→id 收缩序列化器 |
| `http.json.deserializer` | id/code/别名→实体还原、宽松 Boolean、枚举 code 全局反序列化器 |
| `http.util` | `MessageUtils`（静态 i18n 门面）、`HttpUtils`（HttpURLConnection 极简客户端） |
| `http`（根） | Servlet 请求/响应工具（Ajax 判定、客户端 IP、参数拍平、文件下载响应头）+ 遗留设备判定 |
| `util` | 静态工具全家桶：JSON、ID、字符串、集合、枚举、反射、时间、数值、文件、Zip、HTML、设备 |
| `util.sequence` | 雪花算法 `Sequence` + 高并发时钟 `SystemClock` |
| `util.model` | `Device` 值对象 |
| `validate` | JSR-380 校验器实现、`ValidatorHelper` 编程式校验、Service 方法级校验切面 |
| `validate.annotation` | `@EnumValid`、`@PhoneValid` |

## 3. 两条请求处理链路

### 3.1 返回值自动包装链

前置条件：业务 `@Configuration` 上有 `@EnableResultWrapped`。

```
@EnableResultWrapped
  └─ @Import(ResultWrappedConfig)
       └─ @Autowired 方法注入 RequestMappingHandlerAdapter
            └─ 把 ResultWrappedResponseBodyReturnValueHandler 插入返回值 handler 列表，
               位置 = RequestResponseBodyMethodProcessor 之前
```

请求期（handler 内部委托一个持有全部 HttpMessageConverter 和 ControllerAdviceBean 的 `RequestResponseBodyMethodProcessor`）：

1. `supportsReturnType` 与 `RequestResponseBodyMethodProcessor` 相同 → 只拦截 `@ResponseBody`/`@RestController` 方法；`ResponseEntity` 被更靠前的 `HttpEntityMethodProcessor` 处理，**不会被包装**；`void`+`HttpServletResponse` 下载方法因 `requestHandled=true` 根本不进返回值处理，**不会被包装**。
2. `handleReturnValue`：值已是 `Result`，或方法上有 `@UnWrapped`（仅方法级）→ 原样输出；否则 `ResultUtils.success(o)` 后按 JSON 输出（`String` 返回值也会变成 Result JSON）。

对使用者的含义：开启后 Controller 直接返回领域对象即可；`Result` 不会双重包装；退出包装用方法级 `@UnWrapped` 或 `ResponseEntity`。

### 3.2 统一异常处理链

前置条件：`ApiExceptionHandler` 被组件扫描进容器（或业务自写等价 advice，如 sharp-test 的 `TestApiExceptionHandler`）。

```
Controller/Service 抛出
  ├─ BizException            → HTTP 422 + e.getResult()（message 先经 MessageUtils i18n 解析）
  ├─ MaxUploadSizeExceeded   → HTTP 422 + code 5001
  ├─ AccessDeniedException   → HTTP 403 + code 403（spring-security 类，需其在 classpath）
  ├─ Bind/MethodArgumentNotValid/ConstraintViolation/IllegalArgument
  │                          → HTTP 400 + code 400，data = [{field, message, rejectedValue}, ...]
  └─ Exception（兜底）        → HTTP 500 + code 500
```

**Ajax 分叉**：每个 handler 先 `HttpServletRequestUtils.isAjaxRequest(request)`（Accept 含 application/json / X-Requested-With: XMLHttpRequest / 参数 `__ajax=json|xml`）：
- Ajax → 直接返回 `Result`（JSON）；
- 非 Ajax → `BizException` forward `/error/index`（携带 request attribute `result`、`message`），其余 forward `/error`（URI 已是 `/error` 时不再 forward），返回 null。

对使用者的含义：纯前后端分离工程要么保证请求带 JSON Accept 头，要么复制 sharp-test 模式自写不含 forward 的 advice——否则依赖 `/error/index` 视图存在。`ValidatorHelper`/`ServiceMethodValidationInterceptor` 抛出的 `ConstraintViolationException` 正是汇入本链的 400 分支。

## 4. 与 Spring 的集成方式

**为什么没有 AutoConfiguration**：sharp-common 无 `src/main/resources`，无任何 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 或 `spring.factories`（兄弟模块 sharp-database/meta/fileupload/formflow 都有）。它是"类库 + 显式装配"模式，装配责任在业务工程：

| common 中的 Spring 构件 | 注解形态 | 激活方式 |
|---|---|---|
| `ApiExceptionHandler` | `@RestControllerAdvice`（元注解含 `@Component`） | 组件扫描 `com.rick.common.http.exception`，或业务自写等价 advice |
| `MessageUtils` | `@Component` + `@Autowired` setter 注入静态 `MessageSource` | 组件扫描；未注册时静默降级（getMessage 原样返回 code） |
| `SharpWebMvcConfigurer` | **无任何注解**的 `WebMvcConfigurer` 实现 | 业务 `@Configuration` **继承**它（sharp-test `TestConfig` 模式） |
| `ResultWrappedConfig` | `@Configuration` | 仅经 `@EnableResultWrapped` 的 `@Import` 进入容器 |
| `ParamNameProcessor` | 无注解 | 由 `SharpWebMvcConfigurer` 的 `@Bean protected paramNameProcessor()` 注册 |
| `ValidatorHelper` | 无注解 | **由 sharp-database 的 `SharpDatabaseAutoConfiguration` 注册**：`@Bean @ConditionalOnMissingBean @ConditionalOnBean(Validator.class)` |
| `ServiceMethodValidationInterceptor` | `@Aspect`（无 `@Component`） | 仓库内无任何注册点；需业务 `@Bean` 声明 + spring-boot-starter-aop |

**`@Conditional*` 使用情况**：sharp-common 自身源码中**没有**任何 `@Conditional*` 注解（已核实）；条件化只发生在上层 sharp-database 注册 `ValidatorHelper` 时。因此：
- `ValidatorHelper` 可被业务自定义同类型 Bean 覆盖（`@ConditionalOnMissingBean`）；
- common 里其余 Bean 是否可覆盖取决于业务的注册方式——组件扫描场景下，定义自己的 advice（更高 `@Order`）即可优先于 `ApiExceptionHandler` 生效；继承 `SharpWebMvcConfigurer` 本身就是一种"覆盖点"设计（`converterFactories()` 钩子、可覆写 `addFormatters`）。

**静态状态盘点**（线程安全/生命周期相关，影响使用）：
- `JsonUtils`：静态 `ObjectMapper`，配置后只读，线程安全；
- `IdGenerator`：静态 `Sequence(0)` 单例，`nextId()` synchronized；workerId 在**类加载时**取本机 IP 末字节；
- `MessageUtils`：静态 `MessageSource` 引用，Spring 注入后只读；未注入为 null 时走降级分支（非 Web 环境安全，不 NPE）；
- `ParamNameProcessor`：静态 `ConcurrentHashMap` 缓存 Class→别名映射，只增不减（强引用 Class，热部署场景注意）；
- `SystemClock.INSTANCE`：仅 `Sequence(clock=true)` 时启动调度线程，`IdGenerator` 默认路径 clock=false 不启动。

## 5. 设计约束（compileOnly 的含义）

根 build.gradle 对所有子模块施加 `compileOnly`：spring-boot-starter-web / validation / security / aop / jdbc、jakarta.servlet-api。**编译可见、运行不传递**。业务工程必须自备：

| 你要用的能力 | 必须存在的运行时依赖 |
|---|---|
| Controller/包装/异常处理/Servlet 工具/下载 | spring-boot-starter-web（含 spring-webmvc、jackson、jakarta.servlet-api） |
| `@EnumValid`/`@PhoneValid`/`ValidatorHelper` | spring-boot-starter-validation |
| `ApiExceptionHandler` 的 `AccessDeniedException` 分支 | spring-boot-starter-security（缺它时该 handler 方法引用的类不存在，advice 加载会失败——扫描注册 `ApiExceptionHandler` 的工程需要 security 在 classpath） |
| `ServiceMethodValidationInterceptor` | spring-boot-starter-aop |
| `CollectionOps.expectedAsOptional`（spring-dao 异常类）、`ClassUtils` 的 `BeanWrapperImpl` | spring-beans/spring-tx（starter-jdbc 或经 sharp-database 带入） |
| Lombok 注解的实体 | 业务侧 `compileOnly lombok`（common 以 compileOnlyApi 暴露，编译期可见） |

传递提供的（`api` 依赖，无需重复声明）：commons-lang3、commons-codec、guava、commons-collections4、commons-io。

另一个约束：**Jackson 双 mapper**。`JsonUtils` 的静态 mapper 与 Spring MVC 容器 mapper 配置不同（前者无 Long→String、无枚举定制）。对外 HTTP 序列化只信 Spring MVC 那份（由 `SharpWebMvcConfigurer.register(ObjectMapper)` 定制）；`JsonUtils` 用于内部数据交换。

## 6. 扩展点

| 扩展点 | 形式 | 说明 |
|---|---|---|
| `SharpWebMvcConfigurer.converterFactories()` | 可覆写方法（默认返回 null） | 追加 `ConverterFactory`（如 sharp-database 的 `IdToEntityConverterFactory`）；`ConditionalGenericConverter` 类转换器需覆写 `addFormatters`（先 `super.addFormatters(registry)` 再 `registry.addConverter(...)`） |
| `SharpWebMvcConfigurer` 全部 `WebMvcConfigurer` 方法 | 继承覆写 | 业务配置类继承后可继续覆写任何 MVC 定制点 |
| `ExceptionCode` | 接口（错误码 SPI） | 业务枚举实现 `getCode()/getMessage()` 即获得 `throwException()` 断言能力，是错误码体系的标准扩展方式 |
| `JsonStringToObjectConverterFactory.JsonValue` | 标记接口 | 自定义值对象实现它，即可从 GET 查询串 JSON 直接绑定（sharp-test `EmbeddedValue` 模式） |
| 枚举静态 `valueOfCode(String/int)` | 约定式 SPI | `CodeToEnumConverterFactory`、`EnumCustomizeDeserializer`、`EnumUtils`、`@EnumValid` 四处都反射调用它；给枚举加上该方法即接入全部转换/校验链路 |
| 实体 `getId()/setId()`、`getCode()/setCode(String)` | 约定式 SPI | `EntityWithLongIdProperty*` / `EntityWithCodePropertyDeserializer` 的反射契约 |
| `ApiExceptionHandler` | 业务自写同构 advice | 仓库示范：sharp-test `TestApiExceptionHandler`（去掉 forward 的纯 JSON 版） |
| `ValidatorHelper` | `@ConditionalOnMissingBean`（在 sharp-database 注册处） | 业务可自定义 Bean 覆盖 |
| `Sequence` 构造器 | public | 多数据中心/自定义 workerId、时钟回拨容忍、随机序列可自行实例化，不必用 `IdGenerator` 默认单例 |
| `SInfo` | 接口 | `SFunction`/`SConsumer` 的公共元信息契约；可自定义新的可序列化函数接口 extends 它（default 方法由包内 `SInfoHelper` 支撑，注意 `SInfoHelper` 是包级私有，自定义接口需放在 `com.rick.common.function` 包内才能复用——包外请通过 `SFunction`/`SConsumer` 间接使用） |
