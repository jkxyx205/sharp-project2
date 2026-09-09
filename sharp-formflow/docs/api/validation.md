# 校验体系完整参考（form/valid/）

> ⚠️ **这是本模块自研的校验体系，不是 Jakarta Bean Validation（@Valid/@NotNull/hibernate-validator）**。
> 两套体系在本模块并存、职责不同，AI 助手切勿混用：
> - **Jakarta Bean Validation**：只校验「表单定义实体」本身（Form.name @NotBlank、CpnConfigurer.cpnType @NotNull 等），在 FormController/FormService.saveOrUpdate 经 @Validated/@Valid 触发，失败走 MethodArgumentNotValidException/ConstraintViolationException。
> - **自研 Validator 体系**：校验「用户提交的表单值」，由 CpnConfigurer.validators 配置驱动，FormService.post → CpnInstanceProcessor.valid 触发，失败汇总为 BindException（FieldError）。
> 给提交值加 @NotNull 之类的注解是无效的——提交数据是 Map<String,Object>，根本没有类型化注解载体。

## 1. 核心契约

```java
// form/valid/core/Validator.java  🚫 接口
public interface Validator<T> {
    void valid(T t);                        // 校验失败抛 IllegalArgumentException(message)
    ValidatorTypeEnum getValidatorType();   // 类型（配置 JSON 里的 validatorType）
    String getMessage();                    // 失败消息（模板/错误信息里展示）
}

// form/valid/core/AbstractValidator.java  ⭐ 扩展基类
// equals/hashCode 按「类相同」判等（同类不同参视为相等）
```

`ValidatorManager` 🚫（@Component）：注入容器内全部 `Validator` Bean，`afterPropertiesSet` 建静态 `Map<ValidatorTypeEnum, Class<? extends Validator>>`；`REGEX → CustomizeRegex` 是手动 put 的（CustomizeRegex 不是 Bean，因为它需要实例级 regex/message 参数，由 JSON 反序列化创建）。

`CpnConfigurer.getValidatorList()` 反序列化流程：validators（Set\<Map\>，DB JSON）→ 取 `validatorType` → `ValidatorTypeEnum.valueOfCode` → `ValidatorManager.getValidatorClassByType` → Jackson `JsonUtils.toObject(mapJson, validatorClass)` 得到带参校验器实例；同时把组件 `cpnValidators()`（如 Date 的 DateRegex）并入列表头部。

## 2. ValidatorTypeEnum 全量（12 个，code = 枚举名）

| code | label | 实现类 | 是否 Spring Bean |
|---|---|---|---|
| LENGTH | Length | Length | ✓ |
| POSITIVE_INTEGER | Positive Integer | PositiveInteger **和** StringIntegerNumber（⚠️ 双实现） | ✓✓ |
| NUMBER | number | NumberRegex | ✓ |
| REQUIRED | Required | Required | ✓ |
| SIZE | Size | Size | ✓ |
| REGEX | Regex | CustomizeRegex | ✗（ValidatorManager 手动注册） |
| TEXT_NUMBER_SIZE | TextNumberSize | TextNumberSize | ✓ |
| DATE | Date | DateRegex | ✓ |
| TIME | Time | TimeRegex | ✓ |
| EMAIL | Email | EmailRegex | ✓ |
| MOBILE | Mobile | MobileRegex | ✓ |
| DECIMAL | Decimal | DecimalRegex | ✓ |

⚠️ **POSITIVE_INTEGER 歧义**：`PositiveInteger`（校验 Integer ≥ 0）与 `StringIntegerNumber`（校验 String 匹配 `\d+`）都返回 POSITIVE_INTEGER。ValidatorManager 遍历 Set 后写覆盖，注册进 map 的 Class 取决于 Set 迭代顺序 → 配置 `"validatorType":"POSITIVE_INTEGER"` 反序列化出的类**不确定** `[实际生效类需要确认；两者对错误类型入参的行为不同]`。

## 3. 13 个校验规则详表

| 类 | 类型 | 校验对象/逻辑 | 参数（JSON key） | 空值行为 | 失败消息 | 适用组件（validatorSupports 含此类型者） |
|---|---|---|---|---|---|---|
| Required | REQUIRED | Object：null / 空集合 / 空白字符串 → 失败 | `required`(boolean，默认 true；false 时永不失败) | **空值即失败**（这是它的职责） | 必填项需要填写 | 所有组件 |
| Length | LENGTH | String.length ∈ [min, max] | `min`(int)、`max`(int)；构造 `Length(max)` = (0,max) | 空白跳过 | 长度范围 %d - %d 个字符 | Text、TextArea；Email 自带 Length(32) |
| Size | SIZE | Number 转 BigDecimal ∈ [min, max] | `min`(int)、`max`(int) | null 跳过 | 大小范围是%d - %d | IntegerNumber、Currency |
| PositiveInteger | POSITIVE_INTEGER | Integer ≥ 0 | 无 | null 跳过 | 数字必须大于等于0 | ⚠️ 没有任何组件的 internalValidatorSupports 包含 POSITIVE_INTEGER → **只能作为组件 cpnValidators 或类型歧义的一部分出现，数据库配置给组件配它会被 hasValidator 静默跳过** |
| StringIntegerNumber | POSITIVE_INTEGER | String 匹配 `\d+`（纯数字） | 无 | 空白跳过 | 不正确的数字格式 | 同上 |
| TextNumberSize | TEXT_NUMBER_SIZE | 先 `new NumberRegex().valid(value)`（格式），再 BigDecimal ∈ [min,max] | `min`(int)、`max`(int) | ⚠️ NumberRegex 空白跳过、自身空白跳过；但**非数字字符串直接抛「数字格式不正确」** | 大小范围是%d - %d | NumberText |
| CustomizeRegex | REGEX | String.matches(regex) | `regex`(String)、`message`(String) —— **参数完全自定义** | 空白跳过 | 取配置的 message | 无组件在 internalValidatorSupports 里声明 REGEX → 数据库配置会被 hasValidator 跳过 ⚠️（见下方「重要限制」） |
| DateRegex | DATE | String 匹配 `^[1-9]\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])$`（yyyy-MM-dd，不校验真实历法如 2 月 30） | 无 | 空白跳过 | 日期格式不正确，正确的格式是yyyy-MM-dd | Date（组件自带，自动执行） |
| TimeRegex | TIME | String 匹配 `^([01]?[0-9]|2[0-3]):[0-5][0-9]$`（H:mm 或 HH:mm） | 无 | 空白跳过 | 时间格式不正确，正确的格式是HH:mm | Time（组件自带） |
| EmailRegex | EMAIL | String 匹配 `^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$` | 无 | 空白跳过 | 邮箱格式不正确 | Email（组件自带） |
| MobileRegex | MOBILE | String 匹配 `^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(17[013678])|(18[0,5-9]))\d{8}$`（⚠️ 号段是 2021 年的，19x/16x 等不通过） | 无 | 空白跳过 | 号码格式不正确 | Mobile（组件自带） |
| NumberRegex | NUMBER | String 匹配 `-?\d+(\.\d+)?` | 无 | 空白跳过 | 数字格式不正确 | NumberText、Currency（组件自带） |
| DecimalRegex | DECIMAL | String 匹配 `^[+-]?([0-9]+\.?[0-9]*|\.[0-9]+)$`（允许 + 号、.5、5.） | 无 | 空白跳过 | 小数格式不正确 | ⚠️ 无组件 internalValidatorSupports 声明 DECIMAL，也无组件自带 → 数据库配置会被跳过 |

### 重要限制：hasValidator 门槛

`CpnInstanceProcessor.valid` 只执行 `cpn.hasValidator(validator)` 为 true 的校验器，即 **validatorType ∈ 组件的 validatorSupports**（= REQUIRED + 组件自带类型 + internalValidatorSupports）。因此：

- 数据库能给组件配置生效的校验类型**只有**：REQUIRED（所有组件）、LENGTH（Text/TextArea）、SIZE（IntegerNumber/Currency）、TEXT_NUMBER_SIZE（NumberText）。
- DATE/TIME/EMAIL/MOBILE/NUMBER 仅在其对应「自带该 cpnValidator 的组件」（Date/Time/Email/Mobile/NumberText+Currency）上通过 hasValidator——配置了也只是**冗余地再执行一遍**同款校验；在其余组件上**静默跳过**。
- REGEX/DECIMAL/POSITIVE_INTEGER 不在**任何**组件的 validatorSupports 里 → 配置到 validators 一律被静默跳过（DATE 等格式校验本来就由组件自带 cpnValidators 自动执行，无需配置）。
- 想要「自定义正则校验某个文本框」：配置 REGEX **当前不生效**（Text 的 supports 不含 REGEX）→ 需注册自定义组件或改源码 `[替代方案需要确认]`。

## 4. 校验触发时机

1. **服务端（权威）**：`FormService.post`（即 POST/PUT /forms/ajax/**、POST /forms/page/**、直接调 FormService）→ 每字段 `CpnInstanceProcessor.valid()` → 选项校验 + 校验器 → FieldError 累积 → 有错抛 `BindException`（@Transactional 回滚，**一个字段错则整单不存**）。
2. **前端（辅助，仅内置模板）**：tpl/form.html 将 Required→HTML5 `required`、Length.max→`maxlength`、Size.min/max→`min`/`max`、MOBILE→`pattern="1[34578]\d{9}$"`（⚠️ 与服务端 MobileRegex 号段不一致）映射为浏览器原生校验 + Bootstrap `needs-validation` 样式；validator.message 渲染进 `.invalid-feedback` 提示。**没有生成 JS 校验规则文件**，前端仅做 HTML5 属性级预校验。
3. **定义期**：`CpnConfigurerService.saveOrUpdate` 只做 `cpn.check`（选项 label 去重）；「组件是否支持所配校验器」的检查（checkIfAvailable）**未被调用**。

## 5. 配置示例

JSON（存 sys_form_configurer.validators，varchar(512)，Set 结构）：

```json
[
  {"validatorType": "REQUIRED", "required": true},
  {"validatorType": "LENGTH", "min": 2, "max": 20}
]
```

Java（定义表单时）：

```java
CpnConfigurer name = CpnConfigurer.builder()
        .name("userName").label("姓名").cpnType(CpnTypeEnum.TEXT)
        .validatorList(List.of(new Required(), new Length(2, 20)))  // getValidators() 自动转 JSON 落库
        .build();
```

⚠️ validators 列 varchar(512)：校验器 JSON 总长超 512 会落库失败 `[超长行为需要确认]`。

## 6. 扩展自定义校验规则

```java
@Component
public class MyValidator extends AbstractValidator<String> {
    @Override public void valid(String value) {
        if (StringUtils.isNotBlank(value) && !value.startsWith("SH")) {
            throw IllegalArgumentException("必须以 SH 开头");
        }
    }
    @Override public ValidatorTypeEnum getValidatorType() { return ValidatorTypeEnum.REGEX; } // 只能复用已有枚举
    @Override public String getMessage() { return "必须以 SH 开头"; }
}
```

限制（源码依据）：
1. `ValidatorTypeEnum` 是封闭枚举，**无法新增类型值**；新 Bean 只能挂在已有 12 个类型下，且 ValidatorManager 的 map 会被同类型后注册者覆盖（与 POSITIVE_INTEGER 双实现同样的歧义问题）。
2. 即使注册成功，仍受 `hasValidator` 门槛限制：组件 validatorSupports 不含该类型就永远不执行。
3. **实际可行的自定义校验路径**：`FormAdvice.beforeInstanceHandle`（写库前拿到全部 values，自行校验并抛异常）——这是不改源码时唯一可靠的业务校验扩展点。
4. 注册方式：@Component 即可被 ValidatorManager 的 `Set<Validator>` 注入收集（ComponentScan 范围是 `com.rick.formflow.form`，业务自己的包需业务工程自行扫描注册）。
