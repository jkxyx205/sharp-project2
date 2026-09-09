# 示例：FormAdvice 生命周期扩展

> ⚠️ 示例依据模块内部真实调用点（FormService.getFormBOByIdAndInstanceId/handle/delete、PageInstanceController.gotoFormPage）改写。仓库内暂无外部业务调用样例。

`FormAdvice`（`form/service/FormAdvice.java`）**不是** Spring 的 @ControllerAdvice，是本模块的表单生命周期 SPI：全部方法有默认空实现，注册为 Bean 后由 `Form.formAdviceName`（= Bean 名）按表单挂接。FormService 注入 `Map<String, FormAdvice>`（Spring 按 Bean 名收集所有实现），**一个表单只能挂一个 Advice**（formAdviceName 单值）。

自动配置提供默认空实现 Bean（名 `formAdvice`，@ConditionalOnMissingBean）——业务注册任何 FormAdvice Bean 都会替换它；若多个 FormAdvice Bean 共存，@ConditionalOnMissingBean 只是不创建默认的，按名挂接不受影响。

## 钩子触发点全图（源码核实）

```
读取 getFormBO(formId, instanceId)
  ├─ CREATE_TABLE 且有 instanceId：beforeGetInstance 🗑(@Deprecated)
  ├─ init 🗑(@Deprecated)                       —— 新建/回显都触发
  ├─ 字段值填充完成（仅 instanceId 非空）：afterGetInstance(form, instanceId, propertyList, valueMap)
  └─ 返回前（总是）：beforeReturn(form, instanceId, propertyList, valueMap)

页面渲染 PageInstanceController GET（仅页面通道，ajax 通道不触发）
  └─ beforeRender(parameterMap, formBO) → 返回替换后的 FormBO

提交 post(formId[, instanceId], values)
  ├─ 字段校验全部通过后：beforeInstanceHandle(formBO, instanceId, values)   ← 可改 values / 抛异常回滚
  ├─ CREATE_TABLE：insertOrUpdate(values) 返回 true → 跳过默认 EntityDAO 写入（完全接管存储）
  └─ 存储完成后（总是）：afterInstanceHandle(formBO, instanceId, values)     ← 源码注释：mongoDB 文档存储

删除 delete(formId, instanceId)
  ├─ beforeDeleteInstance(instanceId)
  └─ afterDeleteInstance(instanceId)
```

## 完整示例

```java
@Component("contractFormAdvice")
@RequiredArgsConstructor
@Slf4j
public class ContractFormAdvice implements FormAdvice {

    private final MongoTemplate mongoTemplate;      // 示意：自存通道
    private final ContractService contractService;

    /** 提交：写库前加工/业务校验（values 已是组件强类型，可原地修改） */
    @Override
    public void beforeInstanceHandle(FormBO form, Long instanceId, Map<String, Object> values) {
        BigDecimal amount = (BigDecimal) values.get("amount");
        if (amount != null && amount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("合同金额不能超过100万");   // → 400，事务回滚
        }
        values.put("totalWithTax", amount == null ? null : amount.multiply(new BigDecimal("1.06")));
        // ⚠️ 修改 values 只对 CREATE_TABLE（entityDAO.insertOrUpdate(values) 在其后执行）
        //    和 NONE/自存场景生效；INNER_TABLE 的 FormCpnValue 列表在钩子调用**之前**已构建完毕
        //    （FormService.handle L221-243 → L249-259），改 values 不会改变落库内容
    }

    /** 提交：写库后同步文档库（源码注释点名的场景） */
    @Override
    public void afterInstanceHandle(FormBO form, Long instanceId, Map<String, Object> values) {
        mongoTemplate.save(values, "contract_snapshot");
    }

    /** 回显：读出后补充展示数据（propertyList 的 value 可 set） */
    @Override
    public void afterGetInstance(Form form, Long instanceId,
                                 List<FormBO.Property> propertyList, Map<String, Object> valueMap) {
        for (FormBO.Property p : propertyList) {
            if ("status".equals(p.getName()) && p.getValue() != null) {
                p.setValue(p.getValue() + "（已归档）");
            }
        }
    }

    /** 页面渲染前：按 query 参数切换只读等（仅 /forms/page 通道） */
    @Override
    public FormBO beforeRender(Map<String, ?> parameterMap, FormBO formBO) {
        if ("print".equals(parameterMap.get("mode"))) {
            // 可重建/替换 FormBO（@Value 不可变，需 new 一个新的）
        }
        return formBO;
    }

    /** CREATE_TABLE：返回 true 完全接管存储（跳过 EntityDAO.insertOrUpdate） */
    @Override
    public boolean insertOrUpdate(Map<String, Object> values) {
        contractService.saveWithWorkflow(values);   // 例如需要走审批流的自定义写入
        return true;
    }

    @Override
    public void beforeDeleteInstance(Long instanceId) {
        // 例如：校验状态不允许删除 → 抛异常
    }
}
```

挂接：表单定义 `"formAdviceName": "contractFormAdvice"`（或 `form.setFormAdviceName("contractFormAdvice")`）。

## 注意事项

- `init` / `beforeGetInstance` 已 @Deprecated 🗑，勿在新代码使用（用 afterGetInstance）。
- 钩子里抛 `IllegalArgumentException` → ApiExceptionHandler 转 400；抛其他 RuntimeException → 500，事务均回滚（post/delete 有 @Transactional）。
- **ajax JSON 通道不经过 beforeRender**（那是 PageInstanceController 专属）；需要影响 JSON 输出用 beforeReturn。
- beforeInstanceHandle 修改 values 仅影响 CREATE_TABLE/NONE 策略的存储（见示例内注释）；追加的 key 需匹配业务实体属性名才会被 `insertOrUpdate(Map)` 写入。
- FormAdvice Bean 名即挂接 key，改名 = 所有引用它的表单失联（formAdviceName 是字符串弱引用，无外键校验）。
