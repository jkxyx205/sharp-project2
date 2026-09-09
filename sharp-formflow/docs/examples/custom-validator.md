# 示例：自定义校验规则

> ⚠️ 示例依据模块内部真实机制（ValidatorManager.afterPropertiesSet 收集 Validator Bean；CpnInstanceProcessor.valid 的 hasValidator 门槛）改写。仓库内暂无外部业务调用样例。

## 先说结论（源码依据，务必先读）

1. `ValidatorTypeEnum` 封闭（12 个值），**无法新增校验类型 code**；自定义 Validator Bean 只能声明为已有类型。
2. 同类型多实现会在 `ValidatorManager.validatorMap` 互相覆盖（Set 迭代顺序决定），且影响**所有**用该类型配置的 JSON 反序列化 → 用已有类型注册全局 Bean 有副作用，**不推荐**。
3. 执行还有 `cpn.hasValidator` 门槛：组件 validatorSupports 不含该类型 → 配置了也不执行（见 docs/api/validation.md §3）。
4. 因此实际推荐的三条路：
   - **A. 配置级正则**（REGEX/CustomizeRegex）：无需写代码，但 ⚠️ 当前没有任何组件的 supports 包含 REGEX → **对内置组件不生效**，仅当走路线 C 自定义组件把它加进 supports 才可用。
   - **B. FormAdvice 业务校验**（⭐ 最可靠）：提交链路 beforeInstanceHandle 拿到全部已转类型的 values，自行校验、抛异常回滚。
   - **C. 自定义组件类内置校验**：组件 `cpnValidators()` 返回的校验器**无条件执行**（CpnInstanceProcessor 遍历的是 configurer.getValidatorList()，而 getValidatorList 会把 cpnValidators 并进去，且 hasValidator 对自带类型恒真）。给 SINGLE_CHECKBOX 这类自己补实现的组件挂专属校验是干净的。

## 路线 B（推荐）：FormAdvice 做跨字段/业务校验

```java
@Component("entryFormAdvice")     // Bean 名要与 Form.formAdviceName 一致
@RequiredArgsConstructor
public class EntryFormAdvice implements FormAdvice {

    private final EmployeeDAO employeeDAO;

    @Override
    public void beforeInstanceHandle(FormBO form, Long instanceId, Map<String, Object> values) {
        String idCard = (String) values.get("idCard");
        if (idCard != null && !idCard.matches("\\d{17}[\\dXx]")) {
            // 抛 IllegalArgumentException → ApiExceptionHandler 转 400 Result；@Transactional 回滚
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        if (Objects.equals(values.get("startDate"), values.get("endDate"))) {
            throw new IllegalArgumentException("开始日期不能等于结束日期");
        }
    }
}
```

- 注册后在表单定义上挂：`{"form": {..., "formAdviceName": "entryFormAdvice"}}`。
- 执行时机：**所有字段级校验通过之后、写库之前**（FormService.handle L249-253），values 里的值已经是组件强类型（Integer/BigDecimal/List…）。
- 局限：错误不是 FieldError 粒度（不会定位到字段标红），message 是全局的。需要字段级错误时用路线 C 或接受该局限。

## 路线 C：给自定义组件挂专属校验器

```java
@Component
public class SingleCheckBox extends AbstractCpn<String> {

    @Override
    public CpnTypeEnum getCpnType() { return CpnTypeEnum.SINGLE_CHECKBOX; }

    @Override
    public Set<Validator> cpnValidators() {
        // 自带校验：无条件对该类型组件执行，无需（也无法）在 validators 里配置
        return Sets.newHashSet(new IdCardRegex());
    }
}

// 非 Bean 也可（如 CustomizeRegex 模式：由 JSON 反序列化/组件内 new），或注册为 Bean 供 JSON 配置复用
@Setter @Getter @NoArgsConstructor @AllArgsConstructor
public class IdCardRegex extends AbstractValidator<String> {
    private String message = "身份证号格式不正确";
    @Override public void valid(String value) {
        if (StringUtils.isNotBlank(value) && !value.matches("\\d{17}[\\dXx]")) {
            throw new IllegalArgumentException(getMessage());
        }
    }
    @Override public ValidatorTypeEnum getValidatorType() { return ValidatorTypeEnum.REGEX; }
    @Override public String getMessage() { return message; }
}
```

参照内置实现：Date 挂 DateRegex、Email 挂 EmailRegex+Length(32)、Currency 挂 NumberRegex，全是这个模式。

## 路线 A：REGEX 配置（当前对内置组件不生效，仅备查）

```json
{"validatorType": "REGEX", "regex": "^SH\\d{6}$", "message": "必须以SH开头的6位数字"}
```
CustomizeRegex 由 ValidatorManager 手动注册（非 Bean），JSON 反序列化天然支持 regex/message 参数——**但** hasValidator 门槛使它对 TEXT 等内置组件被静默跳过。若未来组件 supports 放开 REGEX（改源码 `Text.internalValidatorSupports()` 加一行），此配置即生效。`[是否有计划放开需要确认]`

## ❌ 反例（都会静默失败或产生全局副作用）

```java
// 反例1：注册一个新类型 Bean 想用 JSON 配置 —— 类型枚举没有对应值，validators JSON 写不出 validatorType
@Component
class MyValidator extends AbstractValidator<String> {
    public ValidatorTypeEnum getValidatorType() { return ???; }   // 无值可返回
}

// 反例2：注册第二个 REQUIRED/LENGTH 类型 Bean —— validatorMap 覆盖，
// 全部表单的 REQUIRED 配置反序列化目标类变成你的类（全局副作用 + Set 顺序不确定）
@Component
class MyRequired extends AbstractValidator<Object> {
    public ValidatorTypeEnum getValidatorType() { return ValidatorTypeEnum.REQUIRED; }  // ❌
}

// 反例3：给 TEXT 组件配置 DATE/DECIMAL/REGEX 等不支持的校验 —— hasValidator 不通过，静默跳过，
// 你以为校验了其实没有
```
