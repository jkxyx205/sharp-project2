# sharp-common 模块使用指南（面向 AI 编程助手）

## 模块定位

**解决什么问题**：为 sharp 系列模块和 `com.rick` 业务工程提供统一基础设施——统一返回结构（`Result`/`ResultUtils`）、统一业务异常（`BizException`/`ExceptionCode`）与全局异常处理（`ApiExceptionHandler`）、返回值自动包装（`@EnableResultWrapped`）、参数别名绑定（`@ParamName`）、校验注解（`@EnumValid`/`@PhoneValid`）、可序列化 Lambda 元信息（`SFunction`/`SConsumer`，sharp-database 的条件 API 基石）、JSON 门面（`JsonUtils`）、雪花 ID（`IdGenerator`）以及一批静态工具类。

**适用场景**：
- `com.rick` 体系内的 Web 业务工程（Controller 返回、异常、校验、JSON、ID）；
- sharp-database / sharp-meta / sharp-fileupload / sharp-formflow 的公共底座（它们都 `api project(':sharp-common')`）。

**不适用场景**：
- 非 Spring Web 的独立工具项目——模块对 Spring（web/validation/security/aop/jdbc）和 `jakarta.servlet-api` 全部是 **compileOnly**，这些能力不会传递给你，缺依赖时部分类会 `NoClassDefFoundError`（如 `CollectionOps` 依赖 spring-dao 类）；
- 需要生产级 HTTP 客户端（`HttpUtils` 无超时/连接池）、需要新手机号段校验（`PhoneValidator` 正则号段老旧）的场景。

## 源码阅读规则

```
默认不要扫描整个模块源码。
使用模块时：
1. 优先阅读 CLAUDE.md
2. 再阅读 API.md
3. 如果 API.md 已经可以解决问题，不要继续阅读源码
4. 只有在文档无法解决问题、排查 Bug 或确认特殊行为时，才阅读相关源码
```

## API 文档索引

- 详细 API（按功能分类 + 8 项说明 + Common Mistakes）→ [API.md](./API.md)
- 架构与 Spring 集成机制、请求处理链路 → [ARCHITECTURE.md](./ARCHITECTURE.md)

## 使用原则

**优先使用**：
- 返回统一结构：`ResultUtils.success/fail`；失败路径一律抛 `BizException` 或用 `ExceptionCode.state/notNull/notExists` 断言，交给 `ApiExceptionHandler`；
- JSON：只用 `JsonUtils`；枚举 code 转换依赖全局注册的 `EnumCustomizeDeserializer`（GET 参数走 `CodeToEnumConverterFactory`）；
- ID：`IdGenerator.getSequenceId()`；
- 时间：`String2TimeUtils`（String→time）/ `Time2StringUtils`（time→String）/ `DateConvertUtils`；
- 集合转 Map：`CollectionOps.map/groupMap/expectedAsOptional`（key 提取器传 `SFunction`，方法引用须指向 getter）；
- 反射：`ClassUtils`（含嵌套属性读写 `setPropertyValue("a.b.c", v)`）。

**禁止重复实现**（模块已有，不要再造）：
- 不要 `new ObjectMapper()`——用 `JsonUtils`；对外 HTTP 序列化交给注册了 `SharpWebMvcConfigurer` 的 Spring MVC mapper（Long→String、枚举、日期都已配置好）；
- 不要自己写全局 `@RestControllerAdvice` 兜底 `Exception`（先复用/继承 `ApiExceptionHandler` 的行为约定，纯 API 工程参考 sharp-test 的 `TestApiExceptionHandler` 模式）；
- 不要自己写雪花 ID、驼峰/下划线转换、日期解析补齐——分别已有 `Sequence`/`StringUtils.camelToSnake`/`String2TimeUtils`；
- 不要为 GET 参数别名写自定义 Resolver——用 `@ParamName`；JSON 别名用 `@JsonAlias`。

**内部实现，业务代码不得直接使用**（🚫）：
- `function.SInfoHelper`（包级私有）、`http.web.param.ParamNameDataBinder`、`http.web.param.ParamNameProcessor`（由 `SharpWebMvcConfigurer` 注册）、`http.web.ResultWrappedResponseBodyReturnValueHandler`、`http.web.config.ResultWrappedConfig`（仅经 `@EnableResultWrapped` 导入）、`util.sequence.SystemClock`。

**已废弃**（🗑，源码 `@Deprecated`）：
- `http.json.deserializer.EnumJsonDeserializer`（多层结构下枚举解析有 bug，被 `EnumCustomizeDeserializer` 取代）；
- `ClassUtils.getFieldGenericClass(Field)` 单参重载（用 `getFieldGenericClass(Class subClass, Field)`）。

## 禁止行为

1. **不要绕过 `ApiExceptionHandler` 自己 try-catch 返回 `Map`**——会破坏 422（业务失败）/400（校验失败，data 为 `{field,message,rejectedValue}` 列表）/500 的状态码约定（证据：`ApiExceptionHandler` 各 `@ExceptionHandler` 的 `@ResponseStatus`）。
2. **不要 `new Result(...)`**——用 `ResultUtils` 静态工厂；`Result` 虽有 `@AllArgsConstructor`，但直接构造容易写错 success/code 组合。
3. **`com.rick.common.util.StringUtils` 与 `org.apache.commons.lang3.StringUtils` 同名共存**（还有 `ClassUtils`/`ObjectUtils`/`EnumUtils`/`FileUtils` 与 Spring/commons 同名）——写代码时逐个核对 import；通用字符串操作直接用 commons-lang3（api 依赖已传递）。
4. **不要把 `ExceptionCode.isNull(obj, msg)` 当 notNull 用**——它的语义是"断言 obj 为 null"，obj 非 null 才抛 `IllegalArgumentException`。
5. **不要期待 `BizException` 返回 HTTP 200**——`ApiExceptionHandler` 对它标了 `@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)`（422）。
6. **不要在 `@RequestBody` 参数对象上用 `@ParamName`**——`ParamNameProcessor.supportsParameter` 显式排除 `@RequestBody`，JSON 别名用 `@JsonAlias`。
7. **不要用 `IdGenerator.getSimpleId()` 做主键**——同毫秒并发靠 3 位随机数去重会碰撞；用 `getSequenceId()`。
8. **`FileUtils.maximumSize(request, name, sizeMB)` 返回 true 表示"已超限"**——语义与命名相反，别写反判断。
9. **不要在 Filter 中调用 `HttpServletRequestUtils.getBodyString` 后期待 Controller 还能读 body**——InputStream 只能消费一次。
10. **不要使用 `ResultWrappedConfig`/`ResultWrappedResponseBodyReturnValueHandler` 等 🚫 内部类**——包装能力只通过 `@EnableResultWrapped` + `@UnWrapped` 暴露。

## 启用方式（业务工程必做）

sharp-common **没有 AutoConfiguration、没有 `META-INF/spring` 注册文件**（已核实源码目录），所有 Spring 能力必须显式装配：

1. **依赖声明**（Gradle）：
   ```gradle
   dependencies {
       api project(':sharp-common')   // 或 implementation
       // sharp-common 的 Spring 依赖是 compileOnly，必须自备：
       implementation 'org.springframework.boot:spring-boot-starter-web'
       implementation 'org.springframework.boot:spring-boot-starter-validation'
       implementation 'jakarta.servlet:jakarta.servlet-api'   // providedRuntime 亦可
       // 用到 ApiExceptionHandler 全量能力还需 security（AccessDeniedException 分支）、aop（ServiceMethodValidationInterceptor）
       // 用到 CollectionOps.expectedAsOptional 需 spring-tx/jdbc（sharp-database 会带入）
       compileOnly 'org.projectlombok:lombok'
   }
   ```
   若工程已依赖 sharp-database（`api project(':sharp-common')`），common 及其 api 依赖（commons-lang3、commons-codec、guava、commons-collections4、commons-io）自动可见。

2. **Web 能力**（Long→String、枚举 code 转换、日期格式、`@ParamName`）：
   ```java
   @Configuration
   public class WebConfig extends SharpWebMvcConfigurer {
       @Override
       public List<ConverterFactory> converterFactories() {
           return List.of(/* 额外的 ConverterFactory，如 sharp-database 的 IdToEntityConverterFactory */);
       }
   }
   ```

3. **返回值自动包装**（可选）：在上面/任一 `@Configuration` 类加 `@EnableResultWrapped`；单接口退出包装在**方法**上加 `@UnWrapped`。

4. **全局异常处理**，二选一：
   - 组件扫描：`@SpringBootApplication(scanBasePackages = {"com.myapp", "com.rick.common"})`（同时激活 `ApiExceptionHandler` 与 `MessageUtils`；注意非 Ajax 请求会 forward `/error/index`，纯 API 工程慎用）；
   - 纯 JSON API：参考 sharp-test 的 `TestApiExceptionHandler`，自写 `@RestControllerAdvice` 复用相同的异常→Result 映射但去掉 forward。

5. **校验**：`ValidatorHelper` Bean 由 sharp-database 自动配置注册（`@ConditionalOnMissingBean` + `@ConditionalOnBean(Validator.class)`）；只用 sharp-common 时自行 `@Bean new ValidatorHelper(validator)`。Service 方法级校验切面 `ServiceMethodValidationInterceptor` 仓库内无人注册，需要时自行 `@Bean` 声明（要求 spring-boot-starter-aop，且类在 `com.rick..service..` 包、类名 `*Service` 结尾、方法有 `@Validated` 而类上没有）。

6. **国际化**（可选）：需要 i18n 时才把 `MessageUtils` 纳入扫描，并提供 `messages.properties`；建议 `spring.messages.use-code-as-default-message=true`，否则未定义 key 的 BizException 中文提示会抛 `NoSuchMessageException`。未注册时 `MessageUtils.getMessage` 静默返回 code 本身，非 Web 环境不会 NPE。
