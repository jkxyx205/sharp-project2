# sharp-formflow 常见错误与排查

> 每条均注明源码依据。分四节：启动失败 / 渲染与页面 / 提交与校验 / 数据与缓存 / 依赖与集成。

## A. 启动失败

### A1. `NoSuchBeanDefinitionException: DictService` / `@Resource DictService` 注入失败
- **触发**：容器中没有 sharp-meta 的 DictService Bean。
- **依据**：`CpnConfigurerDAO` 用 `@Resource private DictService dictService`（强依赖，无 required=false）。
- **处理**：确认 sharp-meta 依赖在 classpath 且其自动配置生效（需要 DataSource → sharp-database 链路完整）。

### A2. FormFlow 自动配置整体未生效（404 /forms/**）
- **触发**：`@ConditionalOnSingleCandidate(GridService.class)` 不满足——无 DataSource、多 DataSource 无主候选、或 sharp-database 自动配置被排除。
- **依据**：FormFlowServiceAutoConfiguration 类注解；GridService 由 SharpDatabaseAutoConfiguration（@ConditionalOnSingleCandidate(DataSource.class)）产出。
- **处理**：先确认 sharp-database 生效（GridService Bean 存在）；多数据源场景标注 @Primary。

### A3. `IllegalArgumentException: cpnMap has been init`
- **触发**：CpnManager.setCpnList 被调用两次（多 Spring 上下文复用同一 JVM 类加载器，如测试上下文重复刷新）。
- **依据**：CpnManager L21-23 显式抛异常；cpnMap 是静态字段。
- **处理**：避免多上下文；测试用 @DirtiesContext 或复用同一上下文。

### A4. 注册自定义组件后启动抛 `IllegalStateException: Duplicate key`
- **触发**：业务新 Cpn Bean 的 getCpnType 与内置 21 个组件之一重复。
- **依据**：CpnManager 用 `Collectors.toMap`（无 merge function）。
- **处理**：见 docs/examples/custom-component.md——只有 SINGLE_CHECKBOX 空位可补，其余类型不可替换。

### A5. 页面接口启动即报模板引擎缺失 / 运行时 `Cannot resolve view`
- **触发**：业务工程没有 spring-boot-starter-thymeleaf。
- **依据**：模板为 Thymeleaf（xmlns:th）；全仓库 build.gradle 均无 thymeleaf 依赖（grep 核实）；模块 web 相关依赖全部 compileOnly。
- **处理**：业务工程添加 `implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'`。

## B. 渲染与页面

### B1. GET /forms/page/{formId} 500：模板不存在（`tpl/form/form` / `form`）
- **触发**：Form.tplName 为空。GET 默认视图名 `"tpl/form/form"`、POST 校验失败默认 `"form"`，**两者在模块 templates 中都不存在**（实际只有 `tpl/form` 和 `success`）。
- **依据**：PageInstanceController L60、L83；resources/templates 目录。
- **处理**：定义表单时显式 `tplName="tpl/form"`（用内置模板）或指向业务自己的模板。

### B2. 附件组件上传按钮无反应 / 浏览器 404 `/plugins/ajaxfileupload.js`
- **触发**：使用内置 tpl/form.html 的 FILE 组件。
- **依据**：form.html L135 引用 `@{/plugins/ajaxfileupload.js}`，而静态文件在 `static/ajaxfileupload.js`（映射为 `/ajaxfileupload.js`）。
- **处理**：复制模板到业务工程修正路径（不要改模块源码）。

### B3. 内置页面样式/脚本加载失败（内网环境）
- **依据**：form.html 硬编码 bootcdn 的 Bootstrap 5.0.2 CSS 与 jQuery 3.6.0。
- **处理**：自定义模板改本地引用。

### B4. 页面接口被当 JSON 调，拿到 HTML
- **依据**：PageInstanceController 是 @Controller 返回视图名；AjaxInstanceController 才是 @RestController。
- **处理**：前端 ajax 一律走 `/forms/ajax/**`；`/forms/page/**` 只用于整页导航/表单原生提交。

### B5. 静态资源冲突：业务的 `/jquery.form2json.js`、`/plugins/**`、`/editable-table/**` 被覆盖或覆盖别人
- **依据**：模块 static/ 根下文件映射到 URL 根（Spring Boot 默认 `/**` → classpath:/static/），多 jar 同名路径按 classpath 顺序取一个。
- **处理**：业务工程避免同名根路径资源；必要时自定义 ResourceHandler。

### B6. DATE 控件没有弹出日期选择器
- **依据**：datepicker 初始化脚本在内置模板 `<script th:inline>` 中，依赖 B2/B3 的 JS 均加载成功；且只对 `cpnType == 'DATE'` 的字段初始化。
- **处理**：检查浏览器 console 的 404；确认组件类型是 DATE 而非 TEXT。

## C. 提交与校验

### C1. 校验「配了但不生效」
- **触发**：给组件配置了它不支持的 validatorType（如 TEXT 配 REGEX/DECIMAL/POSITIVE_INTEGER）。
- **依据**：CpnInstanceProcessor.valid 的 `cpn.hasValidator(validator)` 门槛；validatorSupports = REQUIRED + 自带 + internalValidatorSupports（各组件清单见 docs/api/components.md 末尾矩阵）。**静默跳过，无任何报错**。
- **处理**：只用矩阵内支持的类型；业务规则校验走 FormAdvice.beforeInstanceHandle（docs/examples/custom-validator.md）。

### C2. 提交 500 ClassCastException（CharSequence）
- **触发**：ajax 提交 JSON 中 `"id"` 是数字（`{"id": 487...}`）。
- **依据**：FormService.handle L212 `StringUtils.isNotBlank((CharSequence) values.get("id"))`。
- **处理**：id 一律传字符串 `"487..."`（响应里的 id 本来就是字符串序列化）。

### C3. 校验失败但响应不是 `Result{success:false,data:[{field,message,...}]}`
- **触发**：业务工程未注册 sharp-common 的 `ApiExceptionHandler`（formflow 的 ComponentScan 不含 com.rick.common）。
- **依据**：BindException 由该 @RestControllerAdvice 转 Result（sharp-common ApiExceptionHandler L108-119）；未注册则走 Spring 默认 /error JSON。
- **处理**：业务工程注册 ApiExceptionHandler，或自行 @ExceptionHandler(BindException.class) 转换。⚠️ ApiExceptionHandler 只在 `HttpServletRequestUtils.isAjaxRequest` 为 true 时返回 Result JSON，判定条件（源码核实，满足其一）：`Accept` 含 application/json、`X-Requested-With: XMLHttpRequest`、URI 以 .json/.xml 结尾、参数 `__ajax=json|xml`；否则 forward 到 `/error`。前端调用务必带 `Accept: application/json` 或 XHR 头。

### C4. 提交成功但 FormBO JSON 没包 Result / 或双重包装
- **依据**：AjaxInstanceController.get 返回裸 FormBO；只有注册了 sharp-common `ResultWrappedConfig` 才包 `Result{data:FormBO}`（非 Result 返回值统一包装，@UnWrapped 可豁免）。
- **处理**：确认工程是否注册 ResultWrappedConfig，前端按实际结构解析。

### C5. 使用 SINGLE_CHECKBOX 类型 500 NPE
- **依据**：CpnTypeEnum 有该值但 form/cpn/ 下无实现类 → `CpnManager.getCpnByType` 返回 null（CpnConfigurerService.saveOrUpdate 的 `.check()` 处即 NPE）。
- **处理**：勿用；或按 docs/examples/custom-component.md 示例 1 补实现。

### C6. CHECKBOX/MULTIPLE_SELECT 提交报「没有找到正确的选项」
- **依据**：CheckBox.valid 要求提交值是 options.name 的**子集**；options 来自静态配置或 datasource 字典（Dict.name）。
- **处理**：核对提交的是 option 的 `name` 而不是 `label`；datasource 场景核对 sys_dict 该 type 下的 name 值。

### C7. 页面(form submit)提交 CHECKBOX 组值异常
- **依据**：HttpServletRequestUtils.addArrayValue——多值参数或 `name[]` 结尾转 List\<String\>，单值保持 String；CheckBox.httpConverter 对 String 会自动包装成单元素 List，对 List 直接用。理论上均兼容；若自定义组件强转 List 收到 String[] 会 CCE。
- **处理**：自定义处理逻辑时同时兼容 String 与 List。

### C8. Jakarta @Valid 与自研校验混用困惑
- **依据**：@NotBlank/@NotNull 只出现在 Form/CpnConfigurer/FormCpn/FormCpnValue 实体（定义数据），提交值是 Map，走自研 Validator（docs/api/validation.md 顶部说明）。
- **处理**：给「用户提交值」加 Jakarta 注解没有任何效果；定义实体缺 name/label/cpnType 会在 POST /forms/configs 直接 400（MethodArgumentNotValidException）。

## D. 数据与缓存

### D1. 改了表单定义，页面/接口还是旧结构（**最常见**）
- **依据**：FormUtils.formCacheMap 静态 HashMap，只在缓存 miss 时写入；FormCpnService/CpnConfigurerService/FormController 修改定义后**无任何缓存失效调用**（grep 核实仅 FormService L79/L96 两处引用）。
- **处理**：改定义后执行 `FormUtils.update(formId, null)`（下次读取自动重建）或重启应用；集群需逐节点处理 `[广播方案需要确认]`。

### D2. 高并发下表单结构偶发错乱 / CPU 飙高
- **依据**：formCacheMap 是**非线程安全 HashMap**，并发 get/put（首次加载并发 miss）存在数据竞争。
- **处理**：应用启动后先预热（对每个 formId 调一次 getFormBOById）再放流量；或业务侧加锁封装 `[模块级修复需要确认]`。

### D3. 直接 SQL 查 sys_form_cpn_value 查不出想要的业务数据
- **依据**：INNER_TABLE 是 EAV 纵表：一实例 N 行，value 为 String/JSON（FormCpnValue 实体）；无行转列 API。
- **处理**：按 docs/examples/query-form-data.md §2 的行转列 SQL；强查询需求改用 CREATE_TABLE 策略重建表单。

### D4. 更新表单实例后，部分字段数据丢了
- **依据**：FormService.handle INNER_TABLE 分支先 `deleteByInstanceId` 再插入**本次提交的** values（全量覆盖）；提交 Map 里没带的字段不会保留。
- **处理**：更新必须提交完整字段集（前端先 GET 回显再整体提交，内置模板即此模式）。

### D5. CREATE_TABLE 提交「成功」但业务表没有数据
- **触发**：Form.repositoryName 为空或 Bean 名写错。
- **依据**：handle() CREATE_TABLE 分支仅在 `isNotBlank(repositoryName)` 时 getBean 写入，else 分支整段被注释 → **静默不存储**；Bean 名错误则 NoSuchBeanDefinitionException（500，不是静默）。
- **处理**：repositoryName 必须精确等于业务 `EntityDAOImpl` 子类的 Spring Bean 名（默认类名首字母小写）；读取路径同理（getFormBOByIdAndInstanceId L114）。

### D6. datasource 字典选项没有生效
- **依据**：字典填充在 `CpnConfigurerDAO` 覆写的 `protected select(Class, String sql, Map)` 中；**varargs 路径（selectById → CpnConfigurerService.findById）绕过覆写**。FormService 主链路走 selectByIds（Map 路径）正常填充。另外定义已缓存时（D1）options 也是缓存时的快照——**字典新增项不会实时反映到已缓存表单**（缓存的是填充后的 configIdMap）。
- **处理**：主链路用 getFormBO 即可；字典变更后同样需要刷新表单缓存。

### D7. delete 批量接口返回值恒为 1
- **依据**：FormService.delete(Long, Long[]) L288-294 写死 return 1。
- **处理**：需要真实行数时循环调用单实例 delete。

## E. 依赖与集成

### E1. 附件上传 404 /documents/upload 或行为与 sharp-fileupload 源码不一致
- **依据**：sharp-formflow/build.gradle 以 `com.rick.fileupload:sharp-fileupload:3.0-SNAPSHOT` 引入（exclude sharp-common/sharp-database），而仓库实际版本 0.0.1-SNAPSHOT；根 build.gradle mavenLocal() 优先，`~/.m2/.../3.0-SNAPSHOT`（2024-08-11 产物）存在 → **解析到陈旧 jar**。
- **处理**：`./gradlew :你的模块:dependencies --configuration runtimeClasspath | grep fileupload` 核实实际版本；业务工程显式声明 `com.rick.fileupload:sharp-fileupload:0.0.1-SNAPSHOT`（或当前正确版本）覆盖传递依赖；必要时清理 mavenLocal 陈旧产物。
- 另确认 fileupload 的 DocumentController 已注册（sharp-fileupload 自身的自动配置/扫描 `[注册方式见该模块文档]`）。

### E2. 附件字段存进去的到底是什么？当 ID 用还是当 URL 用？
- **依据**：Attachment/SingleImage 值是**完整文件元数据 JSON**（含 id 和 url，来自 /documents/upload 响应的 Document：id/name/extension/contentType/size/groupName/path/url/fullName），不是纯 ID；tpl/form.html 的 JS 直接把响应对象数组塞进隐藏域。
- **处理**：读取时 `(List<Map<String,Object>>) data.get(name)`，取 `id` 做业务引用、`url` 做展示下载；不要假设只有一个字符串 URL。

### E3. Long id 精度丢失（前端 JS）
- **依据**：EntityId.id 有 `@JsonSerialize(ToStringSerializer)` → 实体 id 输出为字符串；但 **FormBO.data / propertyList[].value 里的裸 Long 值**（如 CREATE_TABLE 业务值）无此保护。
- **处理**：前端把 id 一律当字符串处理；业务值含雪花 Long 时注意 JS Number 精度。

### E4. Form/CpnConfigurer 里 Jakarta 注解报 400 但不知道哪条
- **依据**：定义接口经 @Validated/@Valid 触发 ConstraintViolationException/MethodArgumentNotValidException → ApiExceptionHandler 转 `data:[{field,message,rejectedValue}]`（需已注册，见 C3）。
- **处理**：按 data 数组逐条修正（常见：name/label/cpnType 缺失、code 含非法字符、name 超 32）。
