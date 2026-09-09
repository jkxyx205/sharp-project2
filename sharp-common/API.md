# sharp-common API 文档

> 面向 AI 编程助手的 API 参考。所有签名均从源码逐一核实（JDK 17 / Spring Boot 3.5.7 / Lombok）。
> 包根：`com.rick.common`。推荐等级标记：⭐ 推荐 / ⚠️ 特定场景 / ❌ 不推荐 / 🚫 内部 API / 🗑 已废弃。

## 目录

1. [统一返回结果](#1-统一返回结果)
2. [异常与错误码](#2-异常与错误码)
3. [返回值自动包装](#3-返回值自动包装)
4. [参数绑定与转换](#4-参数绑定与转换)
5. [校验](#5-校验)
6. [Lambda 元信息](#6-lambda-元信息)
7. [JSON](#7-json)
8. [ID 与序列号](#8-id-与序列号)
9. [字符串与集合](#9-字符串与集合)
10. [时间与日期](#10-时间与日期)
11. [数值](#11-数值)
12. [反射与类](#12-反射与类)
13. [文件与压缩](#13-文件与压缩)
14. [HTTP 与设备](#14-http-与设备)
15. [国际化消息](#15-国际化消息)
16. [Common Mistakes](#common-mistakes)

---

# 1. 统一返回结果

类：`com.rick.common.http.model.Result` / `ResultCode` / `ResultUtils`

## Result<T> ⭐

统一响应载体。`@Data @AllArgsConstructor`，字段（全部实例字段，非 static）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | `boolean` | 成功判定标志（前端用这个字段判断，不是 code==200） |
| `code` | `int` | 业务码，见 `ResultCode` |
| `message` | `String` | 提示信息 |
| `data` | `T` | 载荷；标注 `@JsonInclude(NON_NULL)`，**为 null 时不出现在 JSON 中** |

**不要直接 `new Result(...)`，用 `ResultUtils` 静态工厂。**

## ResultCode ⭐

枚举，`getCode()` / `getMessage()`（Lombok `@Getter`）：

| 枚举值 | code | message |
|---|---|---|
| `OK` | 200 | OK |
| `ERROR` | 500 | 服务器端异常 |
| `ARGUMENT_NOT_VALID` | 400 | 参数验证失败 |
| `ACCESS_FORBIDDEN_ERROR` | 403 | 访问未授权 |
| `RESOURCE_NOT_EXISTS_ERROR` | 404 | 资源不存在 |
| `UNPROCESSABLE_ENTITY_ERROR` | 422 | 请求出现错误 |

## ResultUtils.success(data) ⭐

1. **API**：`com.rick.common.http.model.ResultUtils.success(...)`
2. **用途**：构造成功响应。
3. **参数**：
   - `success()` — 无参，data 为 null
   - `success(T data)` — `data`：任意类型，可 null（null 时 JSON 中省略 data 字段）
4. **返回值**：`success()` 返回**原始类型** `Result`（无泛型）；`success(T)` 返回 `Result<T>`。永不为 null。固定 `success=true, code=200, message="OK"`。
5. **示例**：
   ```java
   import com.rick.common.http.model.Result;
   import com.rick.common.http.model.ResultUtils;

   @GetMapping("/users/{id}")
   public Result<User> getUser(@PathVariable Long id) {
       return ResultUtils.success(userService.findById(id));
   }
   ```
6. **使用场景**：不开启 `@EnableResultWrapped` 时手动包装 Controller 返回值；Service 内部需要返回统一结构时。
7. **不应该**：不要 `new Result(true, 200, "OK", data)`；不要在已开启 `@EnableResultWrapped` 的 Controller 里再手动包一层（不会双重包装——handler 对 `Result` 直接放行——但属于冗余代码）。
8. **相关**：`ResultUtils.fail(...)`、`@EnableResultWrapped`。

## ResultUtils.fail(...) ⭐

1. **API**：`ResultUtils.fail(...)`，6 个重载。
2. **用途**：构造失败响应。**正常业务代码应优先抛 `BizException` / 用 `ExceptionCode.state(...)`，由 `ApiExceptionHandler` 统一转成 fail Result**；`fail` 主要用于异常处理器或必须手动返回失败的场合。
3. **参数/返回值**（全部返回非 null）：

   | 签名 | 返回 | 说明 |
   |---|---|---|
   | `fail()` | 原始 `Result` | code=500, message="服务器端异常", data=null |
   | `fail(T data)` | `Result<T>` | code=500, message="服务器端异常"，带 data |
   | `fail(String message)` | 原始 `Result` | code=500, 自定义 message |
   | `fail(int code, String message)` | 原始 `Result` | 自定义 code + message |
   | `fail(String message, T data)` | 原始 `Result` | code=500, message + data |
   | `fail(int code, String message, T data)` | `Result<T>` | 全自定义 |

4. **示例**：
   ```java
   return ResultUtils.fail(4001, "订单已关闭");
   return ResultUtils.fail(ResultCode.ARGUMENT_NOT_VALID.getCode(), "参数验证失败", errorList);
   ```
5. **使用场景**：异常处理器、Filter/Interceptor 中直接写响应（配合 `HttpServletResponseUtils.writeJSON(response, result)`）。
6. **不应该**：
   - `ResultUtils.fail(null)` **无法编译**（`fail(T)` 与 `fail(String)` 二义性），必须写 `fail((String) null)` 或换重载。
   - 注意 `fail("xxx")` 匹配 `String message` 重载；想把一个 String 当 data 传，必须用 `fail(500, "msg", data)` 形式。
7. **相关**：`BizException`、`ApiExceptionHandler`。

---

# 2. 异常与错误码

类：`com.rick.common.http.exception.BizException` / `ExceptionCode` / `ApiExceptionHandler`

## BizException ⭐

1. **API**：`throw new BizException(...)`，继承 `RuntimeException`，非受检。
2. **用途**：业务失败的标准抛法。内部持有一个 `Result`（`getResult()`）和可选 `Object[] params`（`getParams()`，用于 i18n 格式化占位符）。`getMessage()` 即 result 的 message。
3. **参数**（构造器全表，均无默认值）：

   | 构造器 | 说明 |
   |---|---|
   | `BizException(String msg)` | code=500，message=msg |
   | `BizException(String msg, Object[] params)` | 同上，message 会执行 `String.format(msg, params)` |
   | `BizException(int code, String msg)` | 自定义 code |
   | `BizException(int code, String msg, Object[] params)` | 自定义 code + format 参数 |
   | `BizException(int code, String msg, T data)` | 泛型，data 随 Result 返回给前端 |
   | `BizException(int code, String msg, T data, Object[] params)` | 泛型 + format 参数 |
   | `BizException(ExceptionCode exceptionCode)` | 从错误码枚举取 code/message |
   | `BizException(ExceptionCode exceptionCode, Object[] params)` | 同上 + format 参数 |
   | `BizException(Result result)` | 直接持有 Result |
   | `BizException(Result result, Object[] params)` | format 后**会回写** `result.setMessage(...)` |
   | `BizException(Result result, Throwable t)` | 带 cause |
   | `BizException(Result result, Object[] params, Throwable t)` | 带 cause + format |

4. **返回值**：不适用（构造器）。
5. **示例**（sharp-test 同款用法）：
   ```java
   import com.rick.common.http.exception.BizException;

   if (order.isClosed()) {
       throw new BizException(4001, "订单 %s 已关闭", new Object[]{order.getCode()});
   }
   ```
6. **使用场景**：Service/Controller 中一切"业务规则不满足"的场景。抛出后由 `ApiExceptionHandler` 转成 HTTP **422** + fail Result（Ajax 请求）。
7. **不应该**：
   - 不要用 `BizException` 表达参数校验错误（校验交给 `@Valid` / `ValidatorHelper`，走 400）。
   - 不要在 Controller 里自己 `try { ... } catch (BizException e) { return ResultUtils.fail(...) }`，让全局处理器接管。
   - message 会先被 `MessageUtils.getMessage(message, params)` 当 i18n code 解析（见 §15），纯中文提示没问题（未注册 MessageUtils 时原样返回），但注册了 MessageUtils 且 MessageSource 未配置 `useCodeAsDefaultMessage` 时，找不到 code 会抛 `NoSuchMessageException`。
8. **相关**：`ExceptionCode.state(...)`、`ApiExceptionHandler`。

## ExceptionCode ⭐

1. **API**：`com.rick.common.http.exception.ExceptionCode`（接口）+ 静态断言方法。
2. **用途**：业务错误码枚举的标准接口。业务工程定义枚举实现它，即可获得 `throwException()` 和断言能力。接口方法：`int getCode()`、`String getMessage()`。
3. **参数**（静态断言方法）：

   | 方法 | 抛出 | 语义 |
   |---|---|---|
   | `state(boolean expression, String message)` | `expression==false` 时抛 `BizException(message)`（code=500） | 断言条件成立 |
   | `state(boolean expression, String message, T data)` | 同上，带 data | 断言 + 返回数据 |
   | `notExists(String message)` | 直接抛 `BizException(404, message)` | 资源不存在 |
   | `notExists(String message, T data)` | 直接抛 `BizException(404, message, data)` | 同上 |
   | `isNull(Object obj, String message)` | **obj 非 null 时**抛 `IllegalArgumentException(message)` | 断言对象必须为 null |
   | `notNull(Object obj, String message)` | obj 为 null 时抛 `IllegalArgumentException(message)` | 断言对象非 null（走 400） |

   实例 default 方法：`throwException()`（抛 `BizException(getCode(), getMessage())`）、`throwException(Object[] params)`（message 先 `String.format`）。
4. **返回值**：断言方法全部 `void`，不满足即抛异常。
5. **示例**：
   ```java
   import com.rick.common.http.exception.ExceptionCode;
   import lombok.Getter;

   @Getter
   public enum BizError implements ExceptionCode {
       ORDER_CLOSED(4001, "订单已关闭"),
       STOCK_NOT_ENOUGH(4002, "库存不足，剩余 %s");

       private final int code;
       private final String message;

       BizError(int code, String message) {
           this.code = code;
           this.message = message;
       }
   }

   // 使用
   ExceptionCode.notNull(user, "用户不存在");          // IllegalArgumentException → HTTP 400
   ExceptionCode.state(order.isOpen(), "订单已关闭");    // BizException → HTTP 422
   BizError.STOCK_NOT_ENOUGH.throwException(new Object[]{stock}); // BizException(4002, "库存不足，剩余 3")
   ```
6. **使用场景**：模块级错误码集中定义；方法入口断言。
7. **不应该**：注意 `isNull` 语义是"断言为 null"（obj **非** null 才抛），与直觉相反，别当 notNull 用。`IllegalArgumentException` 会被 `ApiExceptionHandler` 归入 400 参数错误，不要用 `notNull/isNull` 表达业务失败。
8. **相关**：`BizException`、`ResultCode`。

## ApiExceptionHandler ⭐（需注册才生效）

1. **API**：`com.rick.common.http.exception.ApiExceptionHandler`，`@RestControllerAdvice`（元注解含 `@Component`）。
2. **用途**：全局异常 → 统一 Result JSON。
3. **参数**：不适用（框架回调）。各 handler 方法签名均为 `(HttpServletRequest, HttpServletResponse, XxxException)`。
4. **返回值/行为**（从源码逐个核实）：

   | 捕获异常 | HTTP 状态 | Result.code | data |
   |---|---|---|---|
   | `BizException` | 422 | e.getResult().getCode() | e.getResult().getData()；message 经 `MessageUtils.getMessage` i18n 解析 |
   | `MaxUploadSizeExceededException` | 422 | 5001（message "文件大小不能超出5M"） | null |
   | `AccessDeniedException`（spring-security） | 403 | 403 | null |
   | `BindException` / `MethodArgumentNotValidException` | 400 | 400 | `List<Map>`，每项 `{field, message, rejectedValue}` |
   | `ConstraintViolationException` | 400 | 400 | 同上格式 |
   | `IllegalArgumentException` | 400 | 400 | `e.getMessage()`（字符串） |
   | 其他 `Exception` | 500 | 500 | null |

   **非 Ajax 请求**（`HttpServletRequestUtils.isAjaxRequest(request)==false`）不返回 JSON：`BizException` forward 到 `/error/index`（request attribute `result`、`message`），其余 forward 到 `/error`（URI 已是 `/error` 时不 forward）。Ajax 判定：`Accept` 含 `application/json`、或 `X-Requested-With: XMLHttpRequest`、或请求参数 `__ajax=json|xml`（源码中还有 URI 与 `.json`/`.xml` 的全等比较，实际几乎不命中）。
5. **示例**：无需调用。注册方式二选一（本仓库无自动配置）：
   ```java
   // 方式 A：组件扫描（sharp-common 所有 @Component/@RestControllerAdvice 一并生效，含 MessageUtils）
   @SpringBootApplication(scanBasePackages = {"com.rick.myapp", "com.rick.common"})

   // 方式 B：纯 JSON API 工程参考 sharp-test：自己写一个 @RestControllerAdvice
   // （TestApiExceptionHandler：同样的异常映射，但去掉 forward，直接返回 Result）
   ```
6. **使用场景**：服务端渲染 + Ajax 混合应用用方式 A；纯前后端分离 API 建议方式 B（复制 sharp-test 的 `TestApiExceptionHandler` 模式，避免依赖 `/error/index` 视图）。
7. **不应该**：业务工程若同时注册了自己的 advice，注意与它的 handler 冲突/顺序问题；不要假设 BizException 返回 HTTP 200 —— 是 **422**，前端 axios/fetch 需按非 2xx 处理。
8. **相关**：`MessageUtils`、`HttpServletRequestUtils.isAjaxRequest`、`ResultUtils`。

---

# 3. 返回值自动包装

类：`com.rick.common.http.web.config.EnableResultWrapped` / `ResultWrappedConfig` 🚫 / `com.rick.common.http.web.ResultWrappedResponseBodyReturnValueHandler` 🚫 / `com.rick.common.http.web.annotation.UnWrapped`

## @EnableResultWrapped ⭐

1. **API**：`@EnableResultWrapped`，`@Target(TYPE)`，`@Import(ResultWrappedConfig.class)`。
2. **用途**：开启"Controller 返回值自动包成 `ResultUtils.success(...)`"。
3. **参数**：无属性。
4. **返回值**：不适用。生效机制：`ResultWrappedConfig` 通过 `@Autowired` 方法拿到 `RequestMappingHandlerAdapter`，把 `ResultWrappedResponseBodyReturnValueHandler` 插到 `RequestResponseBodyMethodProcessor` 之前。
5. **示例**：
   ```java
   import com.rick.common.http.web.config.EnableResultWrapped;

   @Configuration
   @EnableResultWrapped
   public class WebConfig extends SharpWebMvcConfigurer { }
   ```
6. **使用场景**：前后端分离、希望 Controller 直接返回领域对象的工程。
7. **不应该**：需要 Web MVC 环境（容器里必须有 `RequestMappingHandlerAdapter`）；非 Web 工程加它会启动失败。
8. **相关**：`@UnWrapped`、`Result`。

**包装规则**（源码 `ResultWrappedResponseBodyReturnValueHandler.handleReturnValue` 逐条核实）：

| 返回值情形 | 是否包装 |
|---|---|
| 已是 `Result` 类型 | 否，原样输出 |
| 方法上有 `@UnWrapped` | 否 |
| 其他 `@ResponseBody`/`@RestController` 返回值（含 `null`、`String`、POJO） | 是，包成 `ResultUtils.success(o)` 后按 JSON 输出（`String` 返回也变 JSON！） |
| `ResponseEntity<...>` | 否（由排在前面的 `HttpEntityMethodProcessor` 处理，supportsReturnType 不命中） |
| `void` + 注入 `HttpServletResponse` 的下载方法 | 否（Spring 置 requestHandled，不进返回值处理器） |
| 无 `@ResponseBody` 的视图方法 | 否（supportsReturnType 委托 `RequestResponseBodyMethodProcessor`，只认 `@ResponseBody`） |

## @UnWrapped ⭐

1. **API**：`@UnWrapped`，**只能标注在方法上**（`@Target(ElementType.METHOD)`），无属性。
2. **用途**：某个接口退出自动包装，原样输出返回值。
3. **示例**：
   ```java
   import com.rick.common.http.web.annotation.UnWrapped;

   @UnWrapped
   @GetMapping("/raw")
   public Map<String, Object> raw() { return Map.of("k", "v"); } // 输出 {"k":"v"} 而非 Result
   ```
4. **使用场景**：对接第三方回调、需要裸 JSON/字符串的接口。
5. **不应该**：不要试图加在类上（编译不过）；`@RestController` 下文件下载用 `ResponseEntity` 或 `HttpServletResponse`，不需要 `@UnWrapped`。
6. **相关**：`@EnableResultWrapped`。

## ResultWrappedConfig / ResultWrappedResponseBodyReturnValueHandler 🚫

内部装配类与 Handler，业务代码不直接使用、不继承、不注册。

---

# 4. 参数绑定与转换

## @ParamName ⭐

1. **API**：`com.rick.common.http.web.param.ParamName`，`@Target(FIELD)`，属性 `String[] value() default {}`。
2. **用途**：解决 GET 查询参数命名不一致问题——让同一个模型字段接受多个参数名（下划线/驼峰/别名）。**不是**自动的 snake_case→camelCase 转换，必须显式列出别名。
3. **参数**：`value`：允许的请求参数名数组；请求中出现任一别名，其值都会绑定到该字段。
4. **返回值**：不适用。生效前提：`SharpWebMvcConfigurer` 被注册为 Bean（其 `paramNameProcessor()` `@Bean` + `addArgumentResolvers` 插入解析器）。
5. **示例**（sharp-test `User.java` 真实用法）：
   ```java
   import com.rick.common.http.web.param.ParamName;

   public class User {
       @ParamName({"petIds", "pet_ids", "petList"})
       List<Pet> petList;
   }

   // GET /users?id=1&name=Rick&pet_ids=3&pet_ids=4  → petList 绑定 id=3、id=4
   @GetMapping("/users")
   public User getUser(User user) { return user; }   // 模型属性（无 @RequestBody）
   ```
6. **使用场景**：表单/查询串绑定（`ServletModelAttributeMethodProcessor` 路径）。
7. **不应该**：对 `@RequestBody` JSON 参数无效（`ParamNameProcessor.supportsParameter` 明确排除 `@RequestBody`）——JSON 别名请用 Jackson 的 `@JsonAlias`（sharp-test 中两者配套使用）。注解只扫 `getDeclaredFields()`，父类字段上的 `@ParamName` 不会生效。
8. **相关**：`SharpWebMvcConfigurer`、`@JsonAlias`。

## ParamNameProcessor 🚫 / ParamNameDataBinder 🚫

框架管道类。`ParamNameProcessor` 由 `SharpWebMvcConfigurer.paramNameProcessor()` 以 `@Bean` 注册并插入参数解析器首位；含静态 `ConcurrentHashMap` 缓存类→别名映射。业务代码不直接调用。

## SharpWebMvcConfigurer ⭐（继承使用）

1. **API**：`com.rick.common.http.web.SharpWebMvcConfigurer implements WebMvcConfigurer`。**无 `@Configuration`/`@Component` 注解**，必须由业务工程继承并注册为 Bean。
2. **用途**：一处开启本模块全部 Web 能力：
   - `addFormatters`：注册 `CodeToEnumConverterFactory`（字符串按 code 转枚举）+ 子类通过 `converterFactories()` 提供的工厂；
   - `@Autowired(required=false) register(ObjectMapper)`：定制 Spring MVC 的 ObjectMapper —— **Long/long 序列化为 String**（防前端精度丢失）、`LocalDateTime`/`Date` 用 `spring.jackson.date-format`（默认 `yyyy-MM-dd HH:mm:ss`）、`LocalDate` 用 `yyyy-MM-dd`、Enum 反序列化换成 `EnumCustomizeDeserializer`、序列化 `NON_NULL`、空字符串反序列化为 null（`ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`）；
   - `@Bean paramNameProcessor()` + `addArgumentResolvers` 插到首位（`@ParamName` 支持）。
3. **参数**：可覆写钩子 `public List<ConverterFactory> converterFactories()`，默认返回 null；子类返回要额外注册的 `ConverterFactory` 列表。
4. **示例**（sharp-test `TestConfig.java` 真实用法）：
   ```java
   import com.rick.common.http.web.SharpWebMvcConfigurer;

   @Configuration
   @ComponentScan(basePackageClasses = {TestApiExceptionHandler.class})
   public class TestConfig extends SharpWebMvcConfigurer {
       @Override
       public List<ConverterFactory> converterFactories() {
           return List.of(new IdToEntityConverterFactory()); // 来自 sharp-database
       }
   }
   ```
5. **使用场景**：每个 Web 业务工程的标配。
6. **不应该**：`converterFactories()` 只能返回 `ConverterFactory`；`JsonStringToListMapConverter` 等 `ConditionalGenericConverter` 不能用它注册，需要自己覆写 `addFormatters` 先调 `super.addFormatters(registry)` 再 `registry.addConverter(...)`（参考 sharp-database 的 `dbConversionService()` Bean 的注册清单）。
7. **相关**：`@ParamName`、§4 转换器一览、`EnumCustomizeDeserializer`。

## http/convert 转换器一览

| 类 | 转换 | 默认注册? | 等级 |
|---|---|---|---|
| `CodeToEnumConverterFactory` | String → 任意枚举：反射调枚举静态 `valueOfCode(String/int)`，找不到抛 `IllegalArgumentException`（→400） | ✅ 由 `SharpWebMvcConfigurer.addFormatters` 注册 | ⭐ |
| `StringToLocalDateConverterFactory` | String(`yyyy-MM-dd[ HH[:mm[:ss]]]`) → `LocalDate`（走 `String2TimeUtils.toLocalDate`） | ❌ 子类经 `converterFactories()` 注册；sharp-database 的 `dbConversionService` 也注册了它 | ⚠️ |
| `JsonStringToMapConverterFactory` | JSON String → `Map`（`JsonUtils.toObject`），blank → null | ❌ 同上 | ⚠️ |
| `JsonStringToObjectConverterFactory` | JSON String → 实现了其内嵌标记接口 `JsonStringToObjectConverterFactory.JsonValue` 的对象，blank → null | ❌ 同上 | ⭐（自定义值对象接 GET 参数时用；sharp-test `EmbeddedValue` 即实现此接口） |
| `JsonStringToListMapConverter` | JSON String → `List<Map>`（`ConditionalGenericConverter`，需 `addConverter` 注册） | ❌ | ⚠️ |
| `JsonStringToSetMapConverter` | JSON String → `Set<Map>`（同上） | ❌ | ⚠️ |
| `JsonStringToCollectionConverter` | JSON String → `Collection<E>`（按泛型元素类型 `JsonUtils.toList`，同上） | ❌ | ⚠️ |
| `LocalDateTimeToInstantConverter` | `LocalDateTime` → `Instant`（systemDefault 时区） | ❌ | ⚠️ |

---

# 5. 校验

## @EnumValid ⭐

1. **API**：`com.rick.common.validate.annotation.EnumValid`，`@Target({FIELD, METHOD, ANNOTATION_TYPE})`，`@Constraint(validatedBy = EnumValidator.class)`。
2. **用途**：校验入参值必须是目标枚举的合法 code。
3. **参数**（注解属性）：
   - `target()`：`Class<?>`，必填（默认 `Object.class` 无意义），目标枚举类；
   - `message()`：默认 `"枚举值不存在值${validatedValue}"`；
   - `groups()` / `payload()`：标准 JSR-380 属性，默认空。
4. **返回值**：`EnumValidator.isValid`：**null 直接通过**；非 null 时 `EnumUtils.valueOfCode(target, String.valueOf(value))` 非 null 即通过，任何异常视为不通过。
5. **示例**：
   ```java
   import com.rick.common.validate.annotation.EnumValid;

   public class UserQuery {
       @EnumValid(target = WorkStatusEnum.class)
       private String workStatus;   // 传 "INVALID" → 校验失败
   }

   @PostMapping("/users/query")
   public List<User> query(@Valid @RequestBody UserQuery query) { ... }
   ```
6. **使用场景**：查询/命令对象里以 String 或 code 形式接收枚举的字段。校验失败在 Controller 层产生 `MethodArgumentNotValidException`/`BindException` → 400。
7. **不应该**：`target` 忘写会拿 `Object.class` 去反射，永远校验失败（valueOfCode 抛异常→false）；字段类型是枚举本身时不需要它（Jackson 的 `EnumCustomizeDeserializer` 已处理）。
8. **相关**：`EnumUtils.valueOfCode`、`ValidatorHelper`。

## @PhoneValid ⭐

1. **API**：`com.rick.common.validate.annotation.PhoneValid`，`@Target({METHOD, FIELD})`，`@Constraint(validatedBy = PhoneValidator.class)`。
2. **用途**：中国大陆手机号格式校验。
3. **参数**：`message()` 默认 `"手机号码格式不正确"`；`groups()`/`payload()` 默认空。
4. **返回值**：`PhoneValidator.isValid(String)`：null 通过；长度必须 11；正则 `^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(17[013678])|(18[0,5-9]))\d{8}$`。**注意号段老旧**：16x、19x、14x 大部分不支持，按需评估。
5. **示例**：
   ```java
   import com.rick.common.validate.annotation.PhoneValid;

   public class RegisterCmd {
       @PhoneValid
       private String mobile;
   }
   ```
6. **使用场景**：只校验 String 字段。
7. **不应该**：需要新号段时不要改本类语义，业务侧自定义 Constraint。
8. **相关**：`@EnumValid`。

## ValidatorHelper ⭐（Bean 由 sharp-database 注册）

1. **API**：`com.rick.common.validate.ValidatorHelper`，构造器 `ValidatorHelper(jakarta.validation.Validator validator)`（Lombok `@RequiredArgsConstructor`）。
2. **用途**：编程式触发 JSR-380 校验（Service 层手动校验、方法参数校验）。
3. **参数/方法**：
   - `void validate(T target)` — 校验对象全部约束；
   - `void validate(T target, Method methodToValidate, Object[] parameterValues)` — 校验方法参数（`ExecutableValidator.validateParameters`，groups 传空数组）；
   - `void validateProperty(T target, String propertyName)` — 校验单个属性。
4. **返回值**：`void`；**有任何违规即抛 `jakarta.validation.ConstraintViolationException`**（→ `ApiExceptionHandler` 400，data 为 field/message/rejectedValue 列表）。
5. **示例**：
   ```java
   @Service
   @RequiredArgsConstructor
   public class UserService {
       private final ValidatorHelper validatorHelper;

       public void save(User user) {
           validatorHelper.validate(user); // 违规抛 ConstraintViolationException
       }
   }
   ```
6. **使用场景**：Bean 来源：sharp-database 的 `SharpDatabaseAutoConfiguration` 以 `@Bean @ConditionalOnMissingBean @ConditionalOnBean(Validator.class)` 注册；**只用 sharp-common 不用 sharp-database 的工程需自己声明** `@Bean new ValidatorHelper(validator)`。业务可自定义同类型 Bean 覆盖。
7. **不应该**：不要在 Controller `@Valid` 已覆盖的路径上重复调用。
8. **相关**：`ServiceMethodValidationInterceptor`。

## ServiceMethodValidationInterceptor ⚠️（需手动注册）

1. **API**：`com.rick.common.validate.ServiceMethodValidationInterceptor`，`@Aspect`（**无 `@Component`，仓库内无任何地方注册它**；需要业务 `@Bean` 声明且 classpath 有 spring-boot-starter-aop）。
2. **用途**：Service 方法级校验切面。切点：`within(com.rick..service..*) && execution(public * *Service.*(..))` —— 即 `com.rick` 下任意 `service` 包内、类名以 `Service` 结尾的 public 方法。
3. **参数**：`@Autowired ValidatorHelper validatorHelper`（容器中必须存在该 Bean）。
4. **行为**：`@Before` 通知；**仅当目标类上没有 `@Validated` 而方法上有 `@Validated` 时**才执行 `validatorHelper.validate(target, method, args)`（类上有 `@Validated` 时交给 Spring 自己的 MethodValidationPostProcessor，避免重复）。
5. **示例**：
   ```java
   @Configuration
   public class ValidationConfig {
       @Bean
       public ServiceMethodValidationInterceptor serviceMethodValidationInterceptor() {
           return new ServiceMethodValidationInterceptor();
       }
   }

   // com.rick.myapp.service.OrderService（包名必须含 service、类名必须 *Service 结尾）
   public class OrderService {
       @Validated
       public void create(@Valid @NotNull CreateOrderCmd cmd) { ... }
   }
   ```
6. **使用场景**：不满足包名/类名约定的 Service 不会生效——此时给类加 `@Validated` 用 Spring 原生方案。
7. **不应该**：不要假设引入 sharp-common 就有此能力（默认没注册）。
8. **相关**：`ValidatorHelper`。

---

# 6. Lambda 元信息

接口：`com.rick.common.function.SFunction<T,R>` / `SConsumer<T>` / `SInfo`；实现工具 `SInfoHelper` 为**包级私有** 🚫。

## SFunction<T, R> ⭐

1. **API**：`@FunctionalInterface public interface SFunction<T, R> extends SInfo, Serializable`，抽象方法 `R apply(T t)`。
2. **用途**：**可序列化的 Function**。通过 `writeReplace` → `SerializedLambda` 反解出方法引用指向的方法名/属性名/属性类型。是 sharp-database 所有 DAO 方法（`selectByCode(code, SFunction)` 等）与 `CollectionOps` 的参数类型。
3. **参数**（default 方法，均委托 SInfoHelper）：
   - `String getMethodName()` — 实现方法名，如 `getName`；lambda 表达式（非方法引用）时是合成名 `lambda$xxx$0`；
   - `String getPropertyName()` — 方法名以 `get` 开头且长度>3 时去掉 `get` 并首字母小写（`getName`→`name`）；**否则原样返回方法名**；
   - `Class getPropertyType()` — 方法引用返回值类型（从 `instantiatedMethodType` 解析，支持基本类型）；解析失败抛 `RuntimeException`；
   - `boolean isMethodReference()` — 实现方法名不以 `lambda$` 开头且 implMethodKind ∈ {5,6,7} 时为 true；解析异常返回 false。
4. **返回值**：见上。
5. **示例**（sharp-test `SimpleTest` 真实用法）：
   ```java
   import com.rick.common.function.SFunction;
   import com.rick.common.util.CollectionOps;

   SFunction<Pet, String> f = Pet::getName;
   f.isMethodReference();  // true
   f.getPropertyName();    // "name"
   f.getPropertyType();    // String.class

   // 普通 lambda：只能当函数用，取不到有意义的属性名
   SFunction<Pet, Long> g = (SFunction<Pet, Long>) pet -> pet.getUser().getId();
   g.isMethodReference();  // false
   Map<Long, List<Pet>> grouped = CollectionOps.groupMap(pets, g); // 走 apply()
   ```
6. **使用场景**：需要"以类型安全的方式引用属性"的 API（DAO 条件、分组 key）。**方法引用请指向 getter**；指向非 getter（如 `Pet::compute`）时 `getPropertyName()` 返回原方法名。
7. **不应该**：
   - 不要用普通 `java.util.function.Function` 调 sharp-database API（不可序列化，拿不到元信息）；
   - 带状态的捕获 lambda 序列化语义特殊，只用无捕获的方法引用或纯 lambda；
   - 布尔 getter `isActive()` 的 `getPropertyName()` 返回 `isActive`（只剥 `get` 前缀）。
8. **相关**：`SConsumer`、`CollectionOps`、`SInfo`。

## SConsumer<T> ⭐

同 `SFunction`，抽象方法为 `void accept(T t)`，default 元信息方法一致。用于消费型回调（sharp-database 更新回调等）。

## SInfo ⚠️

上述 4 个元信息方法的公共父接口。业务代码一般只面向 `SFunction`/`SConsumer`，不直接实现 `SInfo`。

---

# 7. JSON

## JsonUtils ⭐

1. **API**：`com.rick.common.util.JsonUtils`（final，私有构造，全 static）。
2. **用途**：模块内唯一 JSON 门面，基于 **Jackson**。内部静态 `ObjectMapper` 配置（源码核实）：`FAIL_ON_UNKNOWN_PROPERTIES=false`（多余字段忽略）、注册 `JavaTimeModule`（支持 java.time）、序列化 `Include.NON_NULL`（**null 字段不输出**）。所有方法 `@SneakyThrows`——Jackson 受检异常**原样以 unchecked 抛出**，不吞不包。
3. **参数/方法全表**：

   | 签名 | 返回 | 说明 |
   |---|---|---|
   | `String toJson(Object obj)` | JSON 串 | null 字段省略 |
   | `<T> T toObject(String json, Class<T> clazz)` | T | json 为 null 抛 NPE |
   | `<T> T toObject(InputStream is, Class<T> clazz)` | T | |
   | `<T> T toObject(String json, TypeReference<T> typeRef)` | T | 泛型容器如 `new TypeReference<Map<String, List<User>>>(){}` |
   | `<T> T toObject(JsonNode node, Class<T> clazz)` | T | treeToValue |
   | `<T> List<T> toList(String json, Class<T> clazz)` | `List<T>` | |
   | `<T> List<T> toList(JsonNode node, Class<T> clazz)` | `List<T>` | |
   | `<T> Set<T> toSet(String json, Class<T> clazz)` | `Set<T>` | 实现为 HashSet |
   | `<T> Set<T> toSet(JsonNode node)` | `Set<T>` | ⚠️ 泛型 T 运行期不可知，实际得到 `Set<Object>`（LinkedHashMap 等），慎用 |
   | `<T> Object toObjectFromFile(String fileName, Class<T> clazz)` | Object（实为 T） | 从文件读 |
   | `JsonNode toJsonNode(Object object)` | JsonNode | valueToTree |
   | `JsonNode toJsonNode(String json)` | JsonNode 或 **null** | json 为空串/null 时返回 null |
   | `Map<String, ?> objectToMap(Object obj)` | Map | convertValue |
   | `String beautifyJSON(String json)` | 格式化 JSON | 解析失败抛 `RuntimeException` |

4. **示例**（sharp-common 自带测试同款）：
   ```java
   import com.rick.common.util.JsonUtils;

   String json = JsonUtils.toJson(Map.of("name", "Rick.Xu", "age", 40));
   User user = JsonUtils.toObject(json, User.class);
   List<User> users = JsonUtils.toList(jsonArrayStr, User.class);
   Map<String, List<Long>> m = JsonUtils.toObject(json, new TypeReference<>() {});
   ```
5. **使用场景**：一切手动 JSON 处理。**禁止业务代码自己 `new ObjectMapper()`**。
6. **不应该**：
   - 别指望它输出 null 字段（NON_NULL）；
   - 它的 mapper 与 Spring MVC 的 mapper **不是同一个**：没有 Long→String、没有 `EnumCustomizeDeserializer`，`java.util.Date` 格式也是 Jackson 默认；对外 HTTP 序列化交给 Spring MVC（注册 `SharpWebMvcConfigurer` 后自动配置）；
   - 反序列化失败异常直接向上抛，需要兜底就自己 try-catch `Exception`。
7. **相关**：`EnumCustomizeDeserializer`、http/convert 各 JSON 转换器。

## EnumCustomizeDeserializer ⭐（全局自动生效）

1. **API**：`com.rick.common.http.json.deserializer.EnumCustomizeDeserializer extends JsonDeserializer<Enum<?>> implements ContextualDeserializer`。
2. **用途**：Spring MVC ObjectMapper 的**全局 Enum 反序列化器**——由 `SharpWebMvcConfigurer.register(ObjectMapper)` 以 `simpleModule.addDeserializer(Enum.class, ...)` 注册，业务无需任何注解。
3. **接受的 JSON 形态**（源码核实）：
   - 字符串 → 调枚举静态 `valueOfCode(String)`，无此方法则回退 `Enum.valueOf(name)`；空白串 → null；
   - 整数 → 调 `valueOfCode(int)`，无则回退 `Enum.valueOf(数字字符串)`；
   - 对象 → 必须含 `code` 字段（String 或 Number），按上面规则解析；无 code 抛 `JsonParseException`；
   - null → null；其他 token → 抛 `JsonParseException`。
   - 解析失败抛 `JsonParseException`/`IOException`（→ 400）。
4. **示例**：枚举只要有 `public static XxxEnum valueOfCode(String/int)` 即可：
   ```java
   public enum WorkStatusEnum {
       OPEN("OPEN"), CLOSED("CLOSED");
       private final String code;
       WorkStatusEnum(String code) { this.code = code; }
       public String getCode() { return code; }
       public static WorkStatusEnum valueOfCode(String code) {
           for (WorkStatusEnum e : values()) if (e.code.equals(code)) return e;
           throw new IllegalArgumentException("bad code: " + code);
       }
   }
   // 请求体 {"workStatus": "OPEN"} / {"workStatus": {"code": "OPEN"}} 都能反序列化
   ```
5. **不应该**：`EnumJsonDeserializer` 是它的 🗑 `@Deprecated` 前身（多层结构下有 bug，源码注释明确说已被替换），不要使用。
6. **相关**：`CodeToEnumConverterFactory`（GET 查询参数路径的等价能力）、`EnumUtils`。

## EntityWithLongIdPropertyDeserializer ⭐

1. **API**：`@JsonDeserialize(using = EntityWithLongIdPropertyDeserializer.class)`，标注在**实体/列表字段**上。
2. **用途**：前端只传 id（数字或字符串、单个或数组），后端还原成"只有 id 被填充"的实体（`new` + `setId`）。sharp-database 关联保存场景的核心配套。
3. **参数**：无注解属性。目标类必须有 public 无参构造 + `getId()`/`setId(...)`（`setId` 参数类型取 `getId()` 返回类型）。
4. **返回值/行为**：
   - JSON 数字/文本 → 单实体（文本 blank → id=null 的实体）；
   - 数组且元素是数字/文本 → `List<实体>`（每个只含 id）；空数组 → `Collections.emptyList()`；
   - 数组元素是对象 / 字段是对象 → 走 `JsonUtils` 完整反序列化；
   - 反射失败 `printStackTrace` 后返回 **null**（静默！）。
5. **示例**（sharp-test `User.java`/`Pet.java` 真实用法）：
   ```java
   import com.fasterxml.jackson.annotation.JsonAlias;
   import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
   import com.rick.common.http.json.deserializer.EntityWithLongIdPropertyDeserializer;

   @JsonAlias({"petIds", "pet_ids", "petList"})
   @JsonDeserialize(using = EntityWithLongIdPropertyDeserializer.class)
   List<Pet> petList;

   @JsonAlias({"userId", "user_id", "user"})
   @JsonDeserialize(using = EntityWithLongIdPropertyDeserializer.class)
   User user;

   // 请求体 {"petIds": ["1", 2], "user": {"id": "2"}} 均可
   ```
6. **不应该**：目标类没有 setId/getId 时不会报错，只会得到 null 字段——排查时注意 stderr 的 printStackTrace。
7. **相关**：`EntityWithCodePropertyDeserializer`、`EntityWithLongIdPropertySerializer`、`@ParamName`。

## EntityWithCodePropertyDeserializer ⭐

同上机制，但按 **code（String）** 还原，要求目标类有 public 无参构造 + `setCode(String)`。sharp-test 中用于 `DictValue` 字段（`@JsonDeserialize(using = EntityWithCodePropertyDeserializer.class)` + `@JsonAlias("materialType")`，请求体传 `"materialType": "MATERIAL_A"` 或 `["A","B"]`）。异常同样 printStackTrace → null。

## EntityWithLongIdPropertySerializer ⚠️

1. **API**：`@JsonSerialize(using = EntityWithLongIdPropertySerializer.class)` 标注在实体或 `List<实体>` 字段上。
2. **用途**：序列化时把实体收缩为它的 id（调 `getId()`；List 收缩为 id 数组）。与 Deserializer 成对，可实现"外键字段 JSON 上只暴露 id"。
3. **参数**：无注解属性；目标类必须有 public `getId()`，否则抛 `RuntimeException`。
4. **示例**：
   ```java
   @JsonSerialize(using = EntityWithLongIdPropertySerializer.class)
   private User user;   // 输出 8 而不是整个 user 对象
   ```
5. **使用场景**：仓库内只有注释掉的用例（sharp-test `Pet.java`），行为以源码为准；输出 id 的 Long→String 取决于生效的 ObjectMapper 是否注册了 ToStringSerializer（Spring MVC 的已注册）。
6. **相关**：`EntityWithLongIdPropertyDeserializer`。

## NamePropertyDeserializer ⚠️

1. **API**：`@JsonDeserialize(using = NamePropertyDeserializer.class)` + `@JsonAlias`，标注在 Bean 或 `List<Bean>` 字段上。
2. **用途**：按 **JSON 字段名与属性名的差值**推导要填充的子属性。类 javadoc 给出的约定：属性 `businessPartner`（类型如 `CodeValue`）配 `@JsonAlias("businessPartnerCode")`，收到 `{"businessPartnerCode": "X"}` 时会把值 set 到 `businessPartner.code`（差值 `Code` → 首字母小写 `code`）；列表属性 `businessPartnerList` 配 `@JsonAlias({"businessPartnerCodes", ...})` 同理（属性名尾部 `s`/`List` 会被正则剥离）。差值属性的类型须是 CharSequence 或 Long（Long 时文本会 `Long.parseLong`，空白→null）。
3. **参数**：无注解属性；依赖目标类字段的 getter/setter 存在。
4. **使用场景**：仓库 main 源码中**无实际用例**（仅 javadoc 示例），使用前建议写单测验证 [需要确认：嵌套/别名组合的边界行为未见实测]。
5. **相关**：`EntityWithCodePropertyDeserializer`（更简单直接，优先）。

## BooleanPropertyDeserializer ⚠️

`@JsonDeserialize(using = BooleanPropertyDeserializer.class)` 标注 `Boolean` 字段。宽松布尔转换（源码核实）：null→false；boolean 原样；数组→非空即 true；数字→非 0 为 true；文本 `"false"`/`"0"`/`"否"`→false，其余非空白文本→true，空白→false；其他节点→true。仓库内无用例。注意 `"false"` 之外大小写变体（如 `"FALSE"`）会判为 true。

## EnumJsonDeserializer 🗑

`@Deprecated`。源码注释："已经被 EnumCustomizeDeserializer 替换，这个类在多层结构下，枚举会出现问题"。禁止使用。

---

# 8. ID 与序列号

## IdGenerator.getSequenceId() ⭐

1. **API**：`com.rick.common.util.IdGenerator.getSequenceId()`（`@UtilityClass`，全 static）。
2. **用途**：分布式趋势递增 Long ID（雪花算法升级版）。内部静态 `new Sequence(0)`：dataCenterId=0，**workerId = 本机 IP 最后一个字节**（`0x000000FF & getLastIPAddress()`），clock=false，timeOffset=5ms，randomSequence=false。
3. **参数**：无。
4. **返回值**：`Long`，全局趋势递增、`synchronized` 线程安全。**时钟回拨超过 5ms 抛 `RuntimeException("Clock moved backwards...")`**。
5. **示例**：
   ```java
   Long id = IdGenerator.getSequenceId();
   ```
6. **使用场景**：实体主键、单号。sharp-database 的 `EntityDAOImpl`、sharp-fileupload 均用它。
7. **不应该**：多节点部署时若两台机器 IP 末字节相同（跨网段/NAT），workerId 冲突可能产生重复 ID——大规模集群需自行构造 `new Sequence(dataCenterId, workerId, ...)` 区分（`Sequence` 为 public final 类，构造器 `(long dataCenterId)`、`(long dataCenterId, boolean clock, boolean randomSequence)`、`(long dataCenterId, long workerId, boolean clock, long timeOffset, boolean randomSequence)`；dataCenterId 占 2 位范围 **0~3**，workerId 占 8 位范围 **0~255**，超范围抛 `IllegalArgumentException`。注意类 javadoc 中两个范围写反了，以校验代码为准）。
8. **相关**：`IdGenerator.getSimpleId()`、`Sequence`。

## IdGenerator.getSimpleId() ⚠️

1. **API**：`IdGenerator.getSimpleId()`。
2. **用途**：`System.currentTimeMillis()`（13 位）字符串拼接 3 位随机数（100~999）再 `Long.parseLong`，得 16 位 Long（javadoc 写"19位"与实现不符，以实现为准）。
3. **参数**：无。
4. **返回值**：`Long`。**同一毫秒内并发调用只靠 900 个随机数去重，存在碰撞概率**；每次调用 `new Random()`。
5. **使用场景**：对唯一性要求不高的临时号/文件名后缀。需要唯一 ID 一律用 `getSequenceId()`。
6. **相关**：`Sequence`。

## Sequence ⚠️ / SystemClock 🚫

`Sequence`：见上文构造器说明；`nextId()` 返回 `Long`（synchronized）；`static byte getLastIPAddress()` 取本机 IP 末字节。`SystemClock`：enum 单例（`INSTANCE(1)`），`initialize()/currentTimeMillis()/currentTime()/destroy()`，仅在 `Sequence(clock=true)` 时作为高频时间源，业务不直接用。

---

# 9. 字符串与集合

## StringUtils（com.rick.common.util）⚠️

1. **API**：`com.rick.common.util.StringUtils`（final，静态方法）。
2. **用途**：少量增强。**与 `org.apache.commons.lang3.StringUtils` 同名**——本模块的 commons-lang3 是 `api` 依赖，两个类都在 classpath 上，import 错类是高频事故（sharp-common 自己的 `ApiExceptionHandler` 用的就是 commons-lang3 的）。
3. **方法全表**（签名 + 语义，源码核实）：

   | 签名 | 语义 |
   |---|---|
   | `String appendValue(String value)` | 非空白返回 `" "+value`，否则 `""`（拼 SQL 片段用） |
   | `String generateImgName(String name)` | 头像缩略名：≤2 字符原样；中文取末 2 字；英文取前 2 字母大写 |
   | `boolean isChinese(String str)` | 含 `[一-龥]` 即 true |
   | `String getContent(String htmlStr)` | 剥离 script/style/html 标签取纯文本（异常时输出到 stderr 并返回中间结果） |
   | `String formatURLSeparator(String url)` | `\` 和连续 `/` 归一为单 `/`；null 原样返回 |
   | `String stringToCamel(String str)` | snake→lowerCamel；全大写→小写；UpperCamel→lowerCamel；含 `_` 时先整体转小写再处理 |
   | `String camelToSnake(String camelStr)` | camel→snake_case |
   | `String camelToSpinal(String camelStr)` | camel→kebab-case |
   | `String camelToDot(String camelStr)` | camel→dot.case |
   | `boolean toBoolean(Object value)` | null→false；CharSequence→**非空白即 true（`"false"` 也是 true！）**；Collection→非空即 true；**其他类型一律 false（包括 Boolean.TRUE、数字）** |
   | `String setMethodName(String propertyName)` | `name` → `setName` |
   | `String getMethodName(String propertyName)` | `name` → `getName` |
   | `String uncapitalize(String str)` | 首字母小写；null/empty 原样 |

4. **示例**：
   ```java
   String table = com.rick.common.util.StringUtils.camelToSnake("PurchaseOrder"); // "purchase_order"
   ```
5. **不应该**：判空/截取/大小写等通用操作直接用 commons-lang3 `StringUtils`（已是 api 依赖）；本类只补 camel 转换等增量能力。`toBoolean` 语义特殊，不要当通用布尔解析用。
6. **相关**：`HtmlTagUtils`、`ClassUtils`。

## CollectionOps ⭐

1. **API**：`com.rick.common.util.CollectionOps`（`@UtilityClass`）。
2. **用途**：集合 → Map 的类型安全快捷操作，key 提取器用 `SFunction`。
3. **方法全表**：

   | 签名 | 语义 / 异常 |
   |---|---|
   | `<E> Optional<E> expectedAsOptional(Collection<E> collection)` | 空集合→`Optional.empty()`；**size>1→抛 `IncorrectResultSizeDataAccessException(1, size)`**；否则包首个元素（元素为 null 时 `Optional.ofNullable`） |
   | `<R, T> Map<R, T> map(Collection<T> collection, SFunction<T, R> function)` | 按 key 提取器建 `Map<key, 元素>`。**方法引用**时 key 取 `BeanWrapperImpl.getPropertyValue(propertyName)`（不真正调用你写的 lambda）；普通 lambda 时调 `apply(t)`。**key 重复抛 `IllegalStateException`**（`Collectors.toMap` 语义）；collection 为 null 抛 NPE |
   | `<R, T> Map<R, List<T>> groupMap(Collection<T> collection, SFunction<T, R> function)` | 同上规则分组，key 可重复 |

4. **示例**（sharp-test `SimpleTest` 真实用法，经 sharp-database `OperatorUtils` 委托）：
   ```java
   import com.rick.common.util.CollectionOps;

   Map<Long, User> byId = CollectionOps.map(users, User::getId);
   Map<Long, List<Pet>> byUser = CollectionOps.groupMap(pets, Pet::getUserId);
   User only = CollectionOps.expectedAsOptional(list).orElse(null); // 多于一条会抛异常
   ```
5. **不应该**：`map()` 用于可能重复的 key 时先 `groupMap`；注意 `expectedAsOptional` 是"期望唯一"断言而非安全取值。
6. **依赖警告**：`IncorrectResultSizeDataAccessException` 来自 spring-tx（sharp-common 里是 compileOnly）。**业务工程没有 spring-boot-starter-jdbc/tx 时，加载 CollectionOps 会 `NoClassDefFoundError`**。
7. **相关**：`SFunction`。

## ObjectUtils ⭐

`com.rick.common.util.ObjectUtils`（`@UtilityClass`；与 `org.springframework.util.ObjectUtils` 同名，注意 import）：

| 签名 | 语义 |
|---|---|
| `boolean mayPureObject(Object obj)` | obj 为 null→false；否则按下条判断 |
| `boolean mayPureObject(Class clazz)` | "是否像自定义 POJO"：null、Number、CharSequence、Character、Boolean、枚举、数组、`Temporal`、原始类型、Collection、Map → false；其余 true |
| `Map<String, Object> toMap(Object object)` | 反射 `getDeclaredFields()`（**仅本类字段，不含父类**）→ `Map<字段名, 值>`，setAccessible；IllegalAccessException 抛 `RuntimeException` |

## EnumUtils ⭐

`com.rick.common.util.EnumUtils`（`@UtilityClass`；与 commons-lang3 `EnumUtils` 同名，注意 import）：

| 签名 | 语义 |
|---|---|
| `Enum valueOfCode(Class enumType, String code)` | code 空白→null。枚举有静态 `valueOfCode` 方法：参数是 int/Integer 则 `parseInt` 后调用，否则按 String 调用；调用异常 printStackTrace 后**落到下一行**。没有该方法（或调用失败）→ 回退 `Enum.valueOf(enumType, code)`，**名字不匹配抛 `IllegalArgumentException`**（javadoc 写"返回 null"，与实现不符，以实现为准） |
| `List<String> getCodes(Class enumType)` | ⚠️ 名字有误导：返回 `String.valueOf(枚举常量)` 列表，即 **name()**（除非枚举覆写了 toString），不是 getCode() 的值 |
| `Object getCode(Enum en)` | 反射调 `getCode()`，无此方法/异常 → 返回 `en.name()` |
| `Object getLabel(Enum en)` | 反射调 `getLabel()`，同上回退 name() |
| `Object getData(Enum en, String getMethodName)` | 上两条的通用形式 |

## HtmlTagUtils ⚠️

`isTagPropertyTrueAndPut(Map<String,String> attrMap, String key)`：HTML 布尔属性判定并**回写归一化值**——key 不存在→false；值为空白或值等于 key（`<input readonly readonly>` 风格）→true；否则 `Boolean.valueOf(value)`；最后 `attrMap.put(key, "true"/"false")`。表单渲染场景（sharp-formflow 方向）使用。

---

# 10. 时间与日期

统一约定：全部使用 `ZoneId.systemDefault()`。

## String2TimeUtils ⭐

`com.rick.common.util.String2TimeUtils`（`@UtilityClass`）：

| 签名 | 语义 |
|---|---|
| `LocalDateTime toLocalDateTime(String value)` | 接受 `yyyy-MM-dd`、`yyyy-MM-dd HH`、`yyyy-MM-dd HH:mm`、`yyyy-MM-dd HH:mm:ss` 四种（自动补齐到秒）；blank→null；**其他格式：内部 formatValue 为 null → `LocalDateTime.parse(null,...)` 抛 NPE**，调用前自行保证格式 |
| `LocalDate toLocalDate(String value)` | 上者转 LocalDate；blank→null |
| `LocalTime toTime2(String value)` | 从日期时间串取时间部分 |
| `Instant toInstant(String value)` | null→null；否则按上面规则转 systemDefault 时区 Instant |
| `LocalTime toTime(String value)` | 仅接受 `HH:mm` |
| `String appendStartSuffix(String value)` | `2020-01-01`→`2020-01-01 00:00:00`；`yyyy-MM-dd HH`→`...HH:00:00`；blank→null；其他原样 |
| `String appendEndSuffix(String value)` | `2020-01-01`→`2020-01-01 23:59:59`；`yyyy-MM-dd HH`→`...HH:59:59`；blank→null；其他原样 |

用途：查询区间参数归一（start/end）。`appendStartSuffix`/`appendEndSuffix` + `toLocalDateTime` 是区间查询标准组合。

## Time2StringUtils ⭐

`com.rick.common.util.Time2StringUtils`（`@UtilityClass`），null 入参一律返回 null：

| 签名 | 语义 |
|---|---|
| `String format(Date date)` | `yyyy-MM-dd` |
| `String format(Date date, SimpleDateFormat fmt)` | 自定义 |
| `String format(LocalDate localDate)` | `yyyy-MM-dd` |
| `String format(LocalDateTime dateTime)` | `yyyy-MM-dd HH:mm:ss` |
| `String format(Instant instant)` | `yyyy-MM-dd HH:mm:ss`（systemDefault） |
| `String format(long milliseconds)` | 同上，毫秒时间戳 |
| `String format(long milliseconds, DateTimeFormatter formatter)` | 自定义 |
| `String format(TemporalAccessor temporal, DateTimeFormatter formatter)` | 通用兜底 |

## DateConvertUtils ⭐

| 签名 | 语义 |
|---|---|
| `LocalDate unixTimeToLocalDate(Long milliseconds)` | null 安全（null→null），systemDefault 时区 |
| `LocalDateTime unixTimeToLocalDateTime(Long milliseconds)` | 同上 |

---

# 11. 数值

## BigDecimalUtils ⭐

`com.rick.common.util.BigDecimalUtils`（`@UtilityClass`）。比较方法**入参为 null 会 NPE**（直接 `b1.compareTo(b2)`），先自行判空：

| 签名 | 语义 |
|---|---|
| `boolean eq(BigDecimal b1, BigDecimal b2)` | `compareTo == 0`（忽略 scale，`1.0 eq 1.00` 为 true） |
| `boolean neq / lt / le / gt / ge (BigDecimal, BigDecimal)` | 对应比较 |
| `String formatBigDecimalValue(BigDecimal value)` | 2 位小数 HALF_UP；**value 为 null 返回 `""`** |
| `String formatBigDecimalValue(BigDecimal value, int newScale, RoundingMode roundingMode)` | 自定义精度；先 setScale 再 stripTrailingZeros 再 `String.format("%.Nf", ...)` |

---

# 12. 反射与类

## ClassUtils ⭐

`com.rick.common.util.ClassUtils`（`@UtilityClass`；与 `org.springframework.util.ClassUtils`、commons-lang3 `ClassUtils` 同名，注意 import）：

| 签名 | 语义 |
|---|---|
| `Field getField(Class<?> clazz, String name)` | `FieldUtils.getDeclaredField(clazz, name, true)`——**含父类字段**，forceAccess；找不到返回 null |
| `Field[] getAllFields(Class<?> clazz)` | commons-lang3 `FieldUtils.getAllFields`（含父类） |
| `Class<?>[] getFieldGenericClass(Class<?> subClass, Field field)` | 解析字段泛型实参；`TypeVariable`（如 `List<T>`）沿 subClass 继承链解析成真实类型；无法解析抛 `IllegalArgumentException` |
| `Class<?>[] getFieldGenericClass(Field field)` | 🗑 `@Deprecated`：不传 subClass 时 TypeVariable 只能解析到上界 |
| `Class<?>[] getClassGenericsTypes(Class<?> clazz)` | 父类声明上的泛型实参（`class A extends B<Long>` → `[Long.class]`）；父类无泛型返回 **null** |
| `Object getPropertyValue(Object entity, Field field)` | `field.get`（setAccessible）；entity/field 为 null 抛 `IllegalArgumentException` |
| `Object getPropertyValue(Object entity, String propertyName)` | BeanWrapper 读属性；entity 为 null、属性不可读、BeansException 一律返回 **null**（不抛） |
| `void setFieldValue(Object bean, Field field, Object value)` | 直接字段赋值（绕过 setter） |
| `void setPropertyValue(Object bean, String propertyName, Object value)` | 支持 `a.b.c` 嵌套路径，中间 null 对象自动无参构造；内部 ConversionService 注册了本模块全部 http/convert 转换器（String→枚举/LocalDate/JSON 对象等都能转）；失败抛 `RuntimeException("Failed to set property: ...")` |

示例：
```java
Field f = ClassUtils.getField(User.class, "petList");       // 父类字段也能拿到
Object v = ClassUtils.getPropertyValue(user, "user.name");  // 嵌套读，user 为 null 时返回 null
ClassUtils.setPropertyValue(user, "address.city", "上海");   // address 为 null 会自动 new Address()
```

## ReflectUtils ⭐

`Field[] getAllFields(Class<?> clazz)`：本类+所有父类的 `getDeclaredFields()` 拼接（含 synthetic 字段；与 `ClassUtils.getAllFields` 功能重叠，后者基于 commons-lang3 更成熟，优先用后者）。

---

# 13. 文件与压缩

## FileUtils ⚠️

`com.rick.common.util.FileUtils`（`@UtilityClass`；与 `org.apache.commons.io.FileUtils`、sharp 其他模块文件工具同名，注意 import）：

| 签名 | 语义 |
|---|---|
| `String getContentType(String fileName)` | 按扩展名猜 MIME（html/css/js/gif/jpeg/png/…/apk/exe/mp4…），未知或无扩展名 → `application/octet-stream` |
| `String getFilenameExtension(@Nullable String path)` | 最后一个 `.` 之后的部分；无扩展名/null → null；`.` 在最后一个 `/` 之前 → null |
| `String stripFilenameExtension(String path)` | 去掉扩展名 |
| `String getFilename(String path)` | 最后一个 `/` 之后的部分 |
| `String fullName(String name, String extension)` | extension 空白 → 原样 name；否则 `name + "." + extension` |
| `boolean isImageType(String path)` | 匹配 `FileConstants.IMAGE_PATH_REGEX`（bmp/png/jpeg/jpg/gif/ico，忽略大小写） |
| `boolean isImageType(String path, String contentType)` | 上者为 true，或 contentType 以 `image` 开头 |
| `Boolean isImageType(HttpServletRequest request, String name)` | ⚠️ 强转 `MultipartHttpServletRequest`（非 multipart 请求抛 `ClassCastException`），取上传文件原始名的扩展名判断 |
| `Boolean maximumSize(HttpServletRequest request, String name, int size)` | ⚠️ 同样强转 multipart；**文件 size ≥ size MB 时返回 true（true = 超限）**，命名与语义相反，小心 |

`FileConstants`：`IMAGE_EXTENSION_VALUE = "bmp, png, jpeg, jpg, gif, ico"`、`IMAGE_PATH_REGEX = "(?i)(.*)[.]?(bmp|png|jpeg|jpg|gif|ico)"`。

## ZipUtils ⚠️

`com.rick.common.util.ZipUtils`（final，私有构造）：

| 签名 | 语义 |
|---|---|
| `void zipFiles(String srcDirName, String fileName, String descFileName)` | 把 `srcDirName` 下的 `fileName`（`"*"` 或 `""` = 全部）压缩到 `descFileName`；目录不存在等失败只打 debug 日志静默返回，**不抛异常也无成功标志** |
| `boolean unZipFiles(String zipFileName, String descFileName)` | 解压到目标目录，成功 true |
| `void zipDirectoryToZipFile(String dirPath, File fileDir, ZipOutputStream zouts)` | 目录写入既有 ZipOutputStream（调用方负责 close） |
| `void zipFilesToZipFile(String dirPath, File file, ZipOutputStream zouts)` | 单文件写入既有 ZipOutputStream |

---

# 14. HTTP 与设备

依赖 `jakarta.servlet-api`（compileOnly）——只在 Web 环境可用。

## HttpServletRequestUtils ⭐

`com.rick.common.http.HttpServletRequestUtils`（final，私有构造）：

| 签名 | 语义 |
|---|---|
| `boolean isAjaxRequest(HttpServletRequest request)` | `Accept` 含 `application/json`，或 `X-Requested-With` 含 `XMLHttpRequest`，或 URI 与 `.json`/`.xml` 忽略大小写**全等**（几乎不命中），或参数 `__ajax` ∈ {json, xml} |
| `boolean isNotAjaxRequest(HttpServletRequest request)` | 取反 |
| `String getClientIpAddress(HttpServletRequest request)` | 依次查 `X-Forwarded-For`、`Proxy-Client-IP`、`WL-Proxy-Client-IP`、`HTTP_X_FORWARDED_FOR`、`HTTP_X_FORWARDED`、`HTTP_X_CLUSTER_CLIENT_IP`、`HTTP_CLIENT_IP`、`HTTP_FORWARDED_FOR`、`HTTP_FORWARDED`、`HTTP_VIA`、`REMOTE_ADDR`，首个非空且非 "unknown" 的头即返回（**X-Forwarded-For 可能是逗号列表，原样返回**），否则 `getRemoteAddr()` |
| `Map<String, String> getParameterStringMap(HttpServletRequest request)` | 参数拍平为字符串 Map；多值/`name[]` 用 `,` join；参数名的 `[]` 后缀被去掉 |
| `Map<String, String> getParameterStringMap(HttpServletRequest request, boolean skipBlank)` | skipBlank=true 跳过空白值 |
| `Map<String, Object> getParameterMap(HttpServletRequest request)` | 多值保留为 `List<String>` |
| `Map<String, Object> getParameterMap(HttpServletRequest request, boolean skipBlank)` | 同上 |
| `Map<String, Object> getParameterMap(HttpServletRequest request, Map<String, Object> extendParams)` | 请求参数 + 扩展参数合并（扩展覆盖同名） |
| `String getBodyString(HttpServletRequest request)` | 读 body（UTF-8，按行拼接不含换行符）。**会消耗 InputStream**：body 只能读一次，若过滤器/框架已读过将得到空串；IOException 被 printStackTrace 吞掉返回已读部分 |

## HttpServletResponseUtils ⭐

`com.rick.common.http.HttpServletResponseUtils`（final，私有构造）：

| 签名 | 语义 |
|---|---|
| `OutputStream getOutputStreamAsAttachment(HttpServletRequest request, HttpServletResponse response, String fileName)` | 文件下载（`Content-disposition: attachment`）。设置 `filename` 头（URL 编码）、按浏览器编码文件名（Firefox→RFC2047 Base64；IE/Edge→URLEncoder；其他中文→ISO-8859-1 转码）、ContentType 按扩展名（`FileUtils.getContentType`）；返回 response 的 OutputStream 由调用方写入。抛 `IOException`。**`request.getHeader("User-Agent")` 为 null（如某些客户端）会 NPE** |
| `OutputStream getOutputStreamAsView(HttpServletRequest, HttpServletResponse, String fileName)` | 同上但 `inline`（浏览器内预览） |
| `OutputStream getOutputStream(HttpServletRequest, HttpServletResponse, String fileName, String type)` | type 直接进 Content-disposition（"attachment"/"inline"） |
| `void writeJSON(HttpServletResponse response, String value)` | `application/json; charset=UTF-8` 写字符串 |
| `void writeJSON(HttpServletResponse response, Result result)` | `JsonUtils.toJson(result)` 后写出（Filter/Interceptor 中返回统一错误用） |
| `void write(HttpServletResponse response, String contentType, String value)` | 通用写出；IOException 被 printStackTrace 吞掉 |
| `boolean isMSBrowser(HttpServletRequest request)` | UA 含 MSIE/Trident/Edge；**UA 头缺失 NPE** |

示例（下载）：
```java
@GetMapping("/files/{id}/download")
public void download(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
    FileMeta meta = fileService.find(id);
    try (OutputStream os = HttpServletResponseUtils.getOutputStreamAsAttachment(request, response, meta.getName())) {
        os.write(fileService.content(id));
    }
}
```

## HttpUtils ⚠️

`com.rick.common.http.util.HttpUtils`（基于 `HttpURLConnection` 的极简客户端）：

| 签名 | 语义 |
|---|---|
| `String postJson(String url, Object dataObj) throws Exception` | body 为 `JsonUtils.toJson(dataObj)`，`Content-Type: application/json;charset=UTF-8`；**响应非 200 时不抛异常，返回字符串 `"Error: HTTP xxx"`**——调用方必须检查前缀；非受检包装无，直接 throws Exception |
| `String get(String urlString, Object params) throws Exception` | params 对象 `getDeclaredFields()`（仅本类字段，含 private，setAccessible）拼 `x-www-form-urlencoded` 查询串；2xx 读 body，非 2xx 读 errorStream 并同样返回字符串 |
| `byte[] readAllBytes(InputStream inputStream) throws IOException` | 流读全 |

用途：临时/轻量外部调用。生产级 HTTP 客户端（超时、连接池、重试）请引入 okhttp（版本目录里有）等，本类未设置任何超时。

## DeviceUtils ⭐

`DeviceUtils.getCurrentDevice(HttpServletRequest request)` → `com.rick.common.util.model.Device`（`@Getter @Builder`）：

- `boolean normal` / `mobile` / `tablet`（正则判定 UA；**平板优先**：iPad/Android 无 Mobile/Tablet/Kindle/Silk/PlayBook/Nexus 7|9|10 命中即 tablet，且不再算 mobile）；
- `Platform platform` ∈ `Device.Platform.IOS / ANDROID / UNKNOWN`；
- request 为 null 或无 UA 时安全返回 normal=true、UNKNOWN。

## HttpRequestDeviceUtils ⚠️（遗留）

`isMobileDevice(HttpServletRequest)` / `isIPadDevice(HttpServletRequest)`：基于老旧关键词数组（Nokia/Symbian/移动梦网 Via 头等）的判定，与 `DeviceUtils` 功能重叠且规则陈旧。新代码用 `DeviceUtils`。

---

# 15. 国际化消息

## MessageUtils ⚠️（需组件扫描才生效）

1. **API**：`com.rick.common.http.MessageUtils.getMessage(String code, Object[] params)`（static）。`@Component`，final，私有构造，静态持有 `MessageSource`（`@Autowired` setter 注入）。
2. **用途**：静态方式取 i18n 消息。`ApiExceptionHandler` 处理 `BizException` 时用它对 message 做 i18n 解析。
3. **参数**：`code`：消息 code（**也是找不到时的默认返回值**）；`params`：`String.format`/MessageFormat 占位参数，可为 null。
4. **返回值**：Locale 取 `LocaleContextHolder.getLocale()`。**未注册为 Bean（未被组件扫描）时 `messageSource == null`，原样返回 code（静默降级，不 NPE，非 Web 环境安全）**。已注册时走 `messageSource.getMessage(code, params, locale)`——code 在资源文件中不存在且 MessageSource 未开启 useCodeAsDefaultMessage 会抛 `NoSuchMessageException`。
5. **示例**：
   ```java
   String msg = MessageUtils.getMessage("order.closed", new Object[]{orderCode});
   ```
6. **使用场景**：需要 i18n 时：① 组件扫描包含 `com.rick.common`（或单独注册 MessageUtils Bean）；② 提供 `messages.properties`；③ 建议 `spring.messages.use-code-as-default-message=true`，让未定义的 code（如直接抛的中文提示）原样返回而不是抛异常。不需要 i18n 的工程**什么都不用做**。
7. **不应该**：不要假设它总是可用而去掉降级逻辑；不要在非 Spring 环境期待注入。
8. **相关**：`BizException(msg, params)`、`ApiExceptionHandler`。

---

# Common Mistakes

**1. 绕过统一异常，自己 try-catch 拼 Map**
```java
// ❌ 错误
try { orderService.close(id); } catch (Exception e) {
    return Map.of("success", false, "msg", e.getMessage());
}
// ✅ 正确：直接抛，交给 ApiExceptionHandler
ExceptionCode.state(order.isOpen(), "订单已关闭");
orderService.close(id);
return ResultUtils.success();
```
原因：自拼 Map 丢掉了 422/400/500 状态码约定与 `{field,message,rejectedValue}` 校验错误结构，前端无法统一处理。

**2. import 错 StringUtils / ClassUtils / ObjectUtils / EnumUtils / FileUtils**
```java
// ❌ 想用驼峰转下划线，却导入了 commons-lang3
import org.apache.commons.lang3.StringUtils;
StringUtils.camelToSnake("userName"); // 编译错误：无此方法
// ✅
import com.rick.common.util.StringUtils;
```
原因：本模块 5 个工具类与 commons-lang3 / Spring 的同名类共存于 classpath（commons-lang3 是 api 依赖）。方法找不到时先查 import。

**3. `StringUtils.toBoolean("false")` 返回 true**
```java
// ❌
boolean b = com.rick.common.util.StringUtils.toBoolean("false"); // true！CharSequence 非空白即 true
// ✅ 明确语义时用 commons-lang3
boolean b2 = org.apache.commons.lang3.StringUtils.toBoolean("false"); // 也有坑，推荐 Boolean.parseBoolean
```
原因：`toBoolean(Object)` 对字符串只判非空白，且对 `Boolean`/数字类型一律返回 false。

**4. `ResultUtils.fail(null)` 编译不过 / fail 重载混淆**
```java
// ❌ fail(T data) 与 fail(String message) 二义
Result r = ResultUtils.fail(null);
// ✅
Result r = ResultUtils.fail("操作失败");          // message 重载
Result<Foo> r2 = ResultUtils.fail(500, "失败", fooData); // 带 data 必须三参
```

**5. 以为引入模块就有自动包装/全局异常/参数别名**
```java
// ❌ 只加依赖，Controller 返回裸对象，期待被包成 Result —— 不会发生
// ✅ 三件事都要做：
@Configuration
@EnableResultWrapped                                  // 自动包装
public class WebConfig extends SharpWebMvcConfigurer { } // Web 定制 + @ParamName
@SpringBootApplication(scanBasePackages = {"com.myapp", "com.rick.common"}) // ApiExceptionHandler/MessageUtils
```
原因：sharp-common 没有 AutoConfiguration、没有 `META-INF/spring/*.imports`（已核实），全部 Spring Bean 靠业务侧显式装配。

**6. 忘记继承 SharpWebMvcConfigurer → Long 精度丢失**
```java
// ❌ 未注册时，接口返回 {"id": 1020361648726110208}，JS Number 精度截断为 ...110000
// ✅ 注册后 Long 序列化为 "1020361648726110208"（ToStringSerializer）
```
原因：Long→String、枚举 code 反序列化、日期格式都挂在 `SharpWebMvcConfigurer.register(ObjectMapper)` 上。

**7. BizException 的 HTTP 状态是 422，不是 200**
```js
// ❌ 前端只处理 200 的 success=false
// ✅ 拦截器需处理 4xx/5xx：422 业务失败(Result 结构)、400 校验失败(data 是错误明细数组)
```

**8. @ParamName 用在 @RequestBody 上无效**
```java
// ❌ JSON 参数名映射
public class Cmd { @ParamName({"user_id"}) Long userId; } // @RequestBody 时不生效
// ✅ JSON 用 Jackson
@JsonAlias({"user_id", "user"}) Long userId;
```
原因：`ParamNameProcessor.supportsParameter` 显式排除 `@RequestBody`，只处理查询串/表单模型绑定。

**9. 用 IdGenerator.getSimpleId() 做主键**
```java
// ❌ 同毫秒并发靠 3 位随机数去重，会碰撞
entity.setId(IdGenerator.getSimpleId());
// ✅
entity.setId(IdGenerator.getSequenceId());
```

**10. 没有 spring-jdbc/tx 依赖时用 CollectionOps**
```java
// ❌ 纯工具工程（未引 spring-boot-starter-jdbc）：
CollectionOps.expectedAsOptional(list); // NoClassDefFoundError: org/springframework/dao/IncorrectResultSizeDataAccessException
```
原因：sharp-common 对 Spring 全系是 compileOnly，`org.springframework.dao` 类需业务 classpath 提供。

**11. CollectionOps.map 的 key 重复直接炸**
```java
// ❌ users 里有重复 id 时 IllegalStateException: Duplicate key
Map<Long, User> m = CollectionOps.map(users, User::getId);
// ✅ 可能重复用 groupMap，或自行 stream + merge function
```

**12. 读了两次 request body**
```java
// ❌ Filter 里 getBodyString(request)，Controller 再 @RequestBody —— 后者拿到空流
// ✅ 需要多次读 body 时包一层 ContentCachingRequestWrapper / 自定义可重读 Filter
```
原因：`getBodyString` 直接消费 `getInputStream()`，且 IOException 被吞（只 printStackTrace）。

**13. MessageUtils 扫描注册后，中文提示抛 NoSuchMessageException**
```java
// ❌ 扫描了 com.rick.common，但 messages.properties 没有对应 key：
throw new BizException("订单已关闭"); // ApiExceptionHandler 里 getMessage("订单已关闭", ...) → NoSuchMessageException
// ✅ application.yml: spring.messages.use-code-as-default-message: true
//    或统一用 ExceptionCode 枚举 + 在 messages.properties 定义 key
```

**14. 使用已废弃 API**
```java
// ❌ EnumJsonDeserializer（@Deprecated，多层结构有 bug）
// ❌ ClassUtils.getFieldGenericClass(field)（@Deprecated，泛型变量解析不完整）
// ✅ EnumCustomizeDeserializer（全局已自动注册，通常无需手写）
// ✅ ClassUtils.getFieldGenericClass(subClass, field)
```

**15. 下载响应头 NPE / maximumSize 语义反了**
```java
// ❌ 无 User-Agent 头的调用方触发 getOutputStreamAsAttachment → NPE
// ❌ if (!FileUtils.maximumSize(request, "file", 5)) throw ... // true 才是"超限"！
// ✅ if (FileUtils.maximumSize(request, "file", 5)) throw new BizException(5001, "文件大小不能超出5M");
```

**16. SFunction 拿普通 lambda 的属性名**
```java
// ❌
SFunction<User, String> f = u -> u.getName().trim();
f.getPropertyName(); // "lambda$user$0" 之类的合成名，无意义
// ✅ 方法引用指向 getter
SFunction<User, String> g = User::getName; // getPropertyName() == "name"
```
