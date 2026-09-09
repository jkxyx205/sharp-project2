# 示例：自定义组件（含重要限制说明）

> ⚠️ 示例依据模块内部真实机制（CpnManager.setCpnList 收集容器内全部 Cpn Bean；AbstractCpn 契约）改写。仓库内暂无外部业务调用样例。

## 先说结论：能做什么、不能做什么（源码依据）

| 诉求 | 可行性 | 依据 |
|---|---|---|
| 新增一个全新的组件类型（新 cpnType 字符串） | ❌ **不改模块源码不可行** | `CpnTypeEnum` 是封闭枚举；CpnConfigurer.cpnType 反序列化只认枚举名；表单定义 JSON 里写新类型直接 400 |
| 为**未占用**的枚举值补实现（当前仅 SINGLE_CHECKBOX 无实现） | ✅ 可行 | CpnManager 按 `Set<Cpn>` 收集所有 Bean 建 map，新增 @Component 即注册 |
| 替换/覆盖某内置类型的实现 | ❌ 不可行 | 两个 Bean 返回同一 CpnTypeEnum → `Collectors.toMap` 重复 key → 启动抛 IllegalStateException |
| 自定义某字段的**渲染** | ✅ 用自定义 Thymeleaf 模板（Form.tplName） | 模板完全由业务提供，按 cpnType/additionalInfo 自由渲染 |
| 自定义某字段的**读取值转换** | ✅ `CpnValueConverter`（configurer.cpnValueConverterName） | FormService 读取链路显式支持 |
| 自定义**提交期业务校验/值加工** | ✅ `FormAdvice.beforeInstanceHandle` | post 链路显式回调 |
| 自定义**存储** | ✅ NONE/CREATE_TABLE + `FormAdvice.insertOrUpdate` 返回 true | handle() 中 customInsertOrUpdate 分支 |

→ **大多数"自定义组件"需求应拆解为：内置类型 + 自定义模板渲染 + Converter/Advice 处理值**，而不是写新 Cpn 类。

## 可行示例 1：为 SINGLE_CHECKBOX 补一个实现（唯一无实现的枚举值）

```java
package com.myapp.form;   // 需在业务工程 ComponentScan 范围内注册为 Bean

import com.rick.formflow.form.cpn.core.AbstractCpn;
import com.rick.formflow.form.cpn.core.CpnTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class SingleCheckBox extends AbstractCpn<String> {

    @Override
    public CpnTypeEnum getCpnType() {
        return CpnTypeEnum.SINGLE_CHECKBOX;   // 补上缺失的注册，消除该类型的 NPE
    }
    // parseValue/getStringValue/httpConverter/valid/check 全部继承 AbstractCpn 默认行为：
    // 值 String，非空时必须在 options.name 内，存储原样字符串
}
```

生效机制：`FormServiceConfiguration` 只扫 `com.rick.formflow.form`，**业务包内的 Bean 由业务工程自己的组件扫描注册**；CpnManager 的 `@Autowired setCpnList(Set<Cpn>)` 收集的是整个容器的 Cpn Bean，因此业务 Bean 一样入表。

⚠️ 不要给已占用的类型（TEXT/SELECT/…21 个）再注册实现——启动即炸（toMap 重复 key）。

## 可行示例 2：不写 Cpn 类的"自定义组件"组合拳

需求：一个「员工选择器」，页面是搜索弹窗，存的是员工 id，回显要显示姓名。

```
1. 定义：cpnType=HIDDEN（值 String），additionalInfo 里放自定义渲染参数
   {"name":"employee","label":"员工","cpnType":"HIDDEN","additionalInfo":{"widget":"employee-picker"}}
2. 渲染：Form.tplName 指向业务自定义模板，模板里判断
   p.configurer.additionalInfo.widget == 'employee-picker' 时渲染自己的选择器组件，
   真实值仍写入 name="employee" 的隐藏 input → 提交链路完全复用内置机制
3. 回显转换：cpnValueConverterName = "employeeIdToNameConverter"
   @Component("employeeIdToNameConverter")
   class EmployeeIdToNameConverter implements CpnValueConverter<String, String> {
       public String convert(String id) { return employeeDAO.selectById(Long.parseLong(id)).map(e -> id + "|" + e.getName()).orElse(id); }
   }   // 返回 "id|姓名"，模板拆开显示；存储仍是纯 id
4. 提交校验：FormAdvice.beforeInstanceHandle 里校验员工在职等业务规则（抛异常即回滚）
```

依据：additionalInfo 是 `Map<String,Object>` 自由扩展位（Form/CpnConfigurer 都有）；内置模板对 TABLE 组件的 `additionalInfo.columns` 就是同款用法。

## 如果确实要改模块源码新增 CpnTypeEnum 值

改动点清单（供评估，不推荐——本模块无外部使用者，fork 修改需与 owner 协同）：
1. `CpnTypeEnum` 加枚举值；
2. 新建 `@Component class Xxx extends AbstractCpn<T>`（放 `com.rick.formflow.form.cpn` 包内即被自动扫描）；
3. 如需选项：覆写 `valid`；如需特殊值形态：覆写 `httpConverter`/`parseValue`；
4. 内置模板 `tpl/form.html` 增加该类型的渲染分支（否则页面模式渲染为空白，ajax 模式不受影响）；
5. 校验支持：按需覆写 `internalValidatorSupports()`/`cpnValidators()`。
