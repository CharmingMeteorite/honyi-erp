# 项目代码规范

本规范根据本工作区中的参考项目 `ems` / `ems-vue` 读取分析整理，适用于当前项目 `erp` / `erp-vue`。参考项目只提供代码风格和工程实践，不提供 ERP 的业务边界；库存、物资、设备、领用、审批等模块必须以 ERP 需求为准，不能从参考项目业务直接复制。当前 ERP 的明确技术决策优先于参考项目：前端统一使用 JavaScript，禁止新增 TypeScript。

“观察到”表示参考项目中已经存在的做法；“必须”表示当前 ERP 后续新增或修改代码应遵守的规则。若 ERP 需求确实要求偏离，提交说明中必须写出原因、影响范围和兼容方案。

## 1. 总体原则

- 优先复用本规范和 ERP 已有代码的模式，保持小范围、可读、可验证的改动。
- 不因为网上的架构偏好而引入新的分层、接口、ORM、状态管理或请求库。
- 参考项目业务名称只用于说明代码形态，不代表 ERP 必须拥有同名业务。
- 后端控制器负责 HTTP 接口编排，业务规则放在 Service；数据访问放在 Mapper。
- 输入在边界校验，业务状态和跨表规则在 Service 校验，数据库约束作为最后一道保障。
- 新代码使用 UTF-8、4 个空格缩进的 Java 风格；前端沿用仓库现有的两空格 JavaScript/Vue 风格。

## 2. 后端目录结构

参考项目后端根包为 `com.haucheng.ems`，ERP 根包为 `com.haucheng.erp`。按当前 ERP 根包建立以下职责目录，只有确有代码时才创建目录：

```text
src/main/java/com/haucheng/erp/
├── ErpApplication.java
├── auth/         # 鉴权上下文、拦截器、权限注解
├── common/       # ApiResponse、PageResult、Pagination 等通用对象
├── config/       # Spring 配置和 @ConfigurationProperties
├── controller/   # HTTP 接口
├── dto/          # 请求对象（Request 后缀）
├── entity/       # MyBatis 持久化对象
├── exception/    # BusinessException、GlobalExceptionHandler
├── mapper/       # MyBatis Mapper 接口和注解 SQL
├── service/      # 具体 Service 类
├── utils/        # 只有可复用且无业务归属的工具才放这里
└── vo/           # 对外展示对象（View 后缀）
```

参考项目没有 `repository`、`service.impl`、MapStruct 或 XML Mapper。当前 ERP 不应为了形式完整而增加这些层；如果未来需求需要，可在偏离说明中解释。

## 3. 后端命名规范

- 类名使用 UpperCamelCase：`InventoryController`、`InventoryService`、`InventoryMapper`、`Inventory`、`InventoryCreateRequest`、`InventoryView`。
- Controller、Service、Mapper 使用领域名加职责后缀；Service 直接使用一个具体类，不强制配套接口和 `ServiceImpl`。
- Entity 使用领域实体名；系统对象可以保留 `Sys` 前缀。
- DTO 使用动作或用途加 `Request`，例如 `StockInRequest`、`StatusUpdateRequest`；不要把请求对象命名为 `DTO`，除非已有模块已经如此命名。
- VO 使用对外语义加 `View`，例如 `InventoryView`、`InventoryDetailView`；聚合结果也使用 `View`，简单结果可使用 `Result` 后缀。
- 方法名使用动词或动词加对象：`getPage`、`getById`、`getOptions`、`create`、`update`、`updateStatus`、`delete`、`complete`、`cancel`。批处理使用 `createBatch`、`cancelBatch` 形式。
- URL 使用 `/api` 前缀和复数资源名，如 `/api/inventories`、`/api/inventories/{id}`；动作子路径使用短横线或资源动作现有形式，如 `/{id}/reset-password`、`/{id}/status`。
- Java 字段和 JSON 属性使用 lowerCamelCase；数据库字段使用 snake_case，由 MyBatis 下划线转驼峰映射。
- Vue 页面使用 `XxxView.vue`，布局使用 `XxxLayout.vue`，可复用弹窗或编辑器使用描述性 `XxxDialog.vue`、`XxxEditor.vue`；基础组件使用 PascalCase 文件名。
- JavaScript 变量和函数使用 lowerCamelCase，组件和页面文件使用 PascalCase；常量使用 UPPER_SNAKE_CASE。

## 4. Controller 规范

- 使用 `@RestController`、`@RequestMapping("/api/...")`、`@RequiredArgsConstructor`；需要参数约束时在类上使用 `@Validated`。
- 读接口使用 `@GetMapping`，创建使用 `@PostMapping`，完整更新使用 `@PutMapping`，局部状态或字段修改使用 `@PatchMapping`，删除使用 `@DeleteMapping`。
- 路径参数使用 `@PathVariable`，查询条件使用 `@RequestParam`，JSON 请求体使用 `@Valid @RequestBody`；文件上传使用 `MultipartFile` 和 `multipart/form-data`。
- 需要权限的类或方法使用 `@RequirePermission`。类级权限表示默认查看权限，方法级权限表示创建、修改、删除等更细权限。
- 默认返回 `ApiResponse<T>`。文件下载、重定向等非 JSON 响应可使用 `ResponseEntity`，这是参考项目中的明确例外。
- Controller 只做参数接收、权限边界、Service 调用和响应包装。不得在其中编写 SQL、事务、跨实体状态规则或复杂数据转换；仅允许很小的当前用户检查、文件响应头拼装等接口编排逻辑。
- 分页接口命名为 `getPage`，接收可选筛选条件、`page`、`size`，返回 `ApiResponse<PageResult<T>>`。选项接口使用 `getOptions` 并返回列表。

示例形态：

```java
@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
@RequirePermission("inventory:view")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ApiResponse<PageResult<InventoryView>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(inventoryService.getPage(keyword, page, size));
    }
}
```

## 5. Service 规范

- 使用 `@Service` 和 `@RequiredArgsConstructor`，通过 `private final` 注入 Mapper、其他 Service 和基础设施。
- 默认直接创建 `XxxService`，不创建无实际价值的接口或 `service.impl`。
- Service 方法负责业务校验、状态判断、规范化输入、组装 Entity/VO、协调多个 Mapper，以及抛出 `BusinessException`。
- 写操作、跨多次数据库调用的操作和需要一致性的外部资源操作使用方法级 `@Transactional`。纯查询不加事务，除非明确需要锁定读取。
- 常见方法沿用 `getPage`、`getById`、`getOptions`、`create`、`update`、`updateStatus`、`delete` 和具体业务动作命名。批量动作使用 `*Batch`。
- 读取实体后显式判断空值并抛 `BusinessException.notFound(...)`；重复、状态冲突、引用阻止删除等情况使用 `conflict(...)`；输入业务不合法使用 `badRequest(...)`。
- 对字符串按参考项目习惯使用 `trim()` 或 `trimToNull` 规范化；分页统一经 `Pagination.of` 限制默认值和最大值。
- Service 不把密码、令牌等敏感字段放进 VO；业务状态常量可用字符串集合、`switch` 或已有枚举风格，但不要在同一模块混用多套表示。
- 复杂操作拆成有意义的私有方法，例如 `requireEntity`、`ensureNameAvailable`、`validateRoles`、`toView`；避免让 Controller 或 Mapper 承担这些职责。

## 6. Repository / Mapper 规范

- 数据访问使用 MyBatis 注解 Mapper；接口放在 `mapper`，使用 `@Mapper`。
- SQL 直接写在 `@Select`、`@Insert`、`@Update`、`@Delete` 中，复杂 SQL 使用 Java 文本块；动态 SQL 使用 `<script>`、`<where>`、`<if>`、`<foreach>`。
- 所有参数使用 `#{...}` 绑定，并用 `@Param` 明确参数名；禁止用 `${...}` 拼接用户输入。
- Mapper 方法命名清晰：`selectPage`、`count`、`selectById`、`selectViewById`、`insert`、`update`、`updateStatus`、`deleteById`。
- 分页使用 `LIMIT #{limit} OFFSET #{offset}`，列表和总数使用匹配的筛选条件；分页计算在 `Pagination`，不要散落在 SQL 中。
- 自增主键插入使用 `@Options(useGeneratedKeys = true, keyProperty = "id")`；不要另行引入 ID 生成框架。
- 批量参数使用 `<foreach>`；需要并发一致性的读取明确写 `FOR UPDATE`，并由外层 Service 事务包围。
- 参考项目未使用 JPA、`@Entity`、`@Table`、`@Column`、MyBatis-Plus、Repository 接口或 XML Mapper。ERP 继续使用 MyBatis，除非有明确的架构决策。

## 7. Entity 规范

- Entity 是面向数据库行的普通 Java 类，不使用 JPA 注解。
- 使用 Lombok `@Data` 生成访问器；字段按数据库列映射后的驼峰命名，类型使用 `Long`、`Integer`、`Boolean`、`LocalDate`、`LocalTime`、`LocalDateTime` 等。
- 可在字段声明处设置业务默认值，例如状态默认为 `true`、数量默认为 `0`；仍须保证数据库默认值与代码语义一致。
- Entity 可用于简单 CRUD 返回，但涉及脱敏、聚合、联表或对外字段裁剪时必须转换为 VO。
- 时间字段通常由 MySQL `CURRENT_TIMESTAMP` 和 `ON UPDATE CURRENT_TIMESTAMP` 维护，代码不增加 ORM 自动填充注解。
- 不在 Entity 中放请求校验注解、权限逻辑或前端展示格式化逻辑。

## 8. DTO 规范

- 请求对象放在 `dto`，使用 `@Data` 和 `XxxRequest` 命名。
- 在字段上使用 Jakarta Validation：`@NotNull`、`@NotBlank`、`@NotEmpty`、`@Size`、`@Min`、`@Max`、`@Pattern`；集合元素可直接写 `List<@NotNull Long>`。
- Controller 的 JSON 请求体使用 `@Valid`；需要方法参数校验时在 Controller 类上使用 `@Validated`。
- 校验消息使用面向用户的中文短句，长度限制与数据库列长度一致。业务相关的跨字段、唯一性、状态校验留在 Service。
- 日期时间字段直接使用 `LocalDate`、`LocalTime`；需要固定 JSON 时间格式时使用 `@JsonFormat(pattern = "HH:mm:ss")` 等现有写法。
- 不在 DTO 中混入 Entity 的创建时间、更新人等只读字段；不使用 Record、Builder 或 MapStruct，除非未来已有代码建立了该模式。

## 9. VO 规范

- 对外展示对象放在 `vo`，使用 `@Data`；需要按构造参数快速建立的聚合结果可使用 `@AllArgsConstructor`。
- VO 字段只保留前端需要的数据，密码哈希、对象存储内部 key 等敏感或内部字段不得返回。
- 列表分页返回 `PageResult<具体 View>`；详情可使用 `XxxDetailView` 包装主对象和子列表；树结构通过 `children` 列表表达。
- 简单选项或无敏感字段的资源可直接返回 Entity，这是参考项目的实际例外；复杂资源优先返回 View。
- 不在 VO 上添加持久化注解；显示名称、计数、聚合字段由 Mapper 查询别名或 Service 组装。

## 10. 注解使用规范

后端实际使用的主要注解如下，新增代码应优先从这套集合中选择：

| 用途 | 注解 |
| --- | --- |
| Spring 启动与组件 | `@SpringBootApplication`、`@Service`、`@Component`、`@Configuration`、`@Bean` |
| Web | `@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@PatchMapping`、`@DeleteMapping` |
| 参数 | `@PathVariable`、`@RequestParam`、`@RequestBody`、`@RequestHeader`、`@Valid`、`@Validated` |
| 事务与配置 | `@Transactional`、`@Value`、`@ConfigurationProperties` |
| Lombok | `@Data`、`@Getter`、`@AllArgsConstructor`、`@RequiredArgsConstructor`、`@Slf4j` |
| MyBatis | `@Mapper`、`@Select`、`@Insert`、`@Update`、`@Delete`、`@Options`、`@Param` |
| 校验 | `@NotNull`、`@NotBlank`、`@NotEmpty`、`@Size`、`@Min`、`@Max`、`@Pattern` |
| JSON | `@JsonFormat` |
| 鉴权 | 自定义 `@RequirePermission` |

参考项目未使用 Swagger/OpenAPI/SpringDoc/Knife4j 注解；当前不应无理由引入 API 文档注解体系。`@Repository` 也没有出现在参考项目的数据库层，Mapper 使用 `@Mapper`。

对 `ems/src/main/java` 生产代码（121 个 Java 文件）的文本统计显示：`@Data` 74 次、`@Transactional` 54 次、`@GetMapping` 42 次、`@RequestParam` 74 次、`@PathVariable` 55 次、`@RequestBody` 38 次、`@Valid` 36 次、`@Size` 45 次、`@NotBlank` 33 次、`@NotNull` 31 次、`@RequiredArgsConstructor` 28 次、`@RequirePermission` 84 次、`@Select` 104 次、`@Mapper` 12 次、`@Service` 14 次、`@RestController` 12 次、`@ExceptionHandler` 7 次。其余常见注解包括 `@Insert` 18 次、`@Update` 21 次、`@Delete` 17 次、`@Options` 14 次、`@PostMapping` 22 次、`@PutMapping` 11 次、`@PatchMapping` 6 次、`@DeleteMapping` 10 次、`@RequestMapping` 12 次、`@RequestHeader` 1 次、`@Validated` 4 次、`@JsonFormat` 2 次、`@Pattern` 5 次、`@Min` 4 次、`@Max` 1 次、`@NotEmpty` 3 次、`@AllArgsConstructor` 9 次、`@Configuration` 4 次、`@ConfigurationProperties` 2 次、`@Bean` 2 次、`@Slf4j` 1 次。统计用于识别习惯，不作为为了凑数量而添加注解的要求。

## 11. 异常处理规范

- 业务错误使用 `BusinessException`，携带整数 `code`、用户消息和 `HttpStatus`；优先使用 `badRequest`（40000）、`notFound`（40400）、`conflict`（40900）工厂方法，其他场景保持同样结构。
- 统一处理器为 `@RestControllerAdvice` 的 `GlobalExceptionHandler`，返回 `ResponseEntity<ApiResponse<Void>>`。
- 参数校验错误使用 40001；不可读 JSON 或字段类型错误使用 40002；未认证使用 40100；无权限使用 40300；资源不存在使用 40400；冲突使用 40901；未处理异常使用 50000。新增错误码应保持按 HTTP 类别分段并避免复用。
- `MethodArgumentNotValidException` 返回第一个 `FieldError` 的默认消息；`ConstraintViolationException`、缺少参数、JSON 解析错误、数据库约束错误分别映射为统一错误。
- 业务异常向前端返回其状态和消息；未知异常只记录服务端堆栈，客户端返回通用消息，不能泄露 SQL、密码或内部路径。
- 鉴权拦截器在 `@RequirePermission` 存在时校验 Bearer Token 和权限，并在请求完成后清理 ThreadLocal 上下文。

## 12. API 返回规范

统一外层结构：

```json
{ "code": 0, "message": "success", "data": {} }
```

- 成功有数据：`ApiResponse.success(data)`，`code=0`、`message="success"`。
- 成功无数据：`ApiResponse.success()`，`data=null`。
- 失败：`ApiResponse.error(code, message)`，`data=null`，HTTP 状态由异常类型决定。
- 列表：`ApiResponse<List<T>>`；分页：`ApiResponse<PageResult<T>>`。
- `PageResult` 固定字段为 `list`、`page`、`size`、`total`。默认页码为 1，默认大小为 20，最大大小为 100；特殊非分页列表由接口自身限制并在 Service 中夹逼。
- 文件下载/导出可返回 `ResponseEntity<byte[]>` 或 302 重定向，这是传输协议需要，不套 JSON；其余 API 不得直接返回裸对象。

## 13. 日志规范

- 使用 Spring Boot 默认日志门面，通过 Lombok `@Slf4j` 获取 Logger；不引入自定义日志工具。
- 参考项目只在全局异常处理器记录日志：数据库约束冲突使用 `warn`，未知异常使用 `error` 并带异常对象。
- Controller 不记录普通成功请求；Service 不为每一步打印日志。新增日志应服务于故障排查、审计或外部调用，不打印密码、Token、文件访问签名和完整敏感请求体。
- 日志消息使用稳定的英文事件短语或项目已有语言风格，异常对象作为最后参数保留堆栈。

## 14. 数据库规范

- 参考技术为 MySQL 8.0 + InnoDB + utf8mb4；列名 snake_case，Java/JSON 使用 camelCase。
- 主键通常为 `BIGINT UNSIGNED AUTO_INCREMENT`，Mapper 使用 generated keys 回填 `Long id`。
- 唯一性、外键、检查约束、索引和状态约束优先落在数据库；Service 先给出可读业务错误，再由 `DataIntegrityViolationException` 兜底。
- 常见审计字段为 `created_at`、`updated_at`，由数据库默认时间和更新时间维护。参考项目没有逻辑删除字段、统一 `deleted` 过滤、乐观锁 `version` 或 ORM 自动填充；ERP 不得无理由假定这些机制存在。
- 分页使用 `LIMIT/OFFSET`；动态条件使用 MyBatis `<where>` 与 `<if>`；批量 ID 使用 `<foreach>`。
- 需要防止并发冲突时，在事务中使用 `FOR UPDATE` 并说明锁定对象；不要仅依赖内存判断唯一性。
- 数据库脚本应可重复执行、避免破坏性默认操作，并把迁移原因写在脚本或文档中。当前阶段不要新增 ERP 业务表。

## 15. 前端目录结构

参考项目使用 `ems-vue/src` 下的以下结构，ERP 前端沿用对应职责：

```text
src/
├── assets/       # base.css、main.css 等全局样式
├── components/   # 可复用组件、Dialog、Editor
├── layouts/      # AppLayout 等应用壳
├── lib/          # api、auth、types、format 和领域辅助函数
├── router/       # Vue Router 配置
├── views/        # 页面，使用 XxxView.vue
├── App.vue
└── main.js
```

参考项目没有 Pinia、Vuex、专门的 `api/` 目录或 composable 目录；共享状态集中在 `lib/auth.ts` 的响应式对象，通用请求在 `lib/api.ts`，类型在 `lib/types.ts`。这些 `.ts` 文件仅是参考项目的观察结果。当前 ERP 使用 `lib/auth.js`、`lib/api.js`，不创建类型文件；只有出现真实复用需求时才拆分新的 composable。

## 16. Vue 组件规范

- 使用 Vue 3 `<script setup>` 和 JavaScript，不使用 Options API 或 `lang="ts"`。
- 页面按“状态与辅助函数 → 生命周期 → template → scoped style”组织；保持页面内相关逻辑相邻。
- 使用 `ref` 管理简单值和列表，`reactive` 管理表单/筛选对象，`computed` 管理派生数据，`watch` 响应弹窗、路由参数或筛选变化，`onMounted` 加载数据，`onBeforeUnmount` 清理事件监听。
- 组件 Props 使用运行时对象形式的 `defineProps({...})`，需要默认值时在字段中声明 `default`；事件使用 `defineEmits([...])`，双向值采用 `update:modelValue`。
- 表单使用 Element Plus `el-form`、`el-form-item`、`FormRules` 和 `FormInstance`；保存前先做必要的前端校验，服务端错误通过 `ElMessage.error` 展示。
- 列表/表格页面维护 `loading`、`saving`、`data`、`filters` 等明确状态；请求在 `try/catch/finally` 中管理加载状态。
- 删除、停用、退出等不可逆或高影响操作使用 `ElMessageBox.confirm`，成功使用 `ElMessage.success`，警告使用 `ElMessage.warning`。
- 弹窗使用 `v-model` 或 `modelValue` + `update:modelValue`；复杂编辑流程可使用 Dialog/Drawer，但不把业务状态放到全局。
- 页面应提供加载、空数据和错误反馈；参考项目使用 `v-loading`、空状态容器和 Element Plus Message。
- 使用 `@element-plus/icons-vue` 图标和 Element Plus 组件；不要引入第二套 UI 组件库。

## 17. API 请求规范

- 使用 `src/lib/api.js` 中基于原生 `fetch` 的封装；不引入 axios，除非有明确兼容性理由。
- 请求方法使用 JavaScript 函数：`api.get(path, params)`、`api.post(path, data)`、`api.put(path, data)`、`api.patch(path, data)`、`api.delete(path)`。
- GET 查询参数通过 URLSearchParams 生成，忽略 `undefined`、`null` 和空字符串；JSON 请求自动设置 `Content-Type`，FormData 不手动设置该头。
- 从 `localStorage` 的项目 Token 键读取 Bearer Token 并注入 `Authorization`；401 清理 Token 并跳转 `/login`。
- 请求只返回服务端 envelope 的 `data`；非 2xx、空响应或 `code !== 0` 统一转成 `Error`，页面负责显示消息。
- 文件上传使用 `XMLHttpRequest` 以获得进度回调；下载使用 fetch、Blob 和临时 `<a>`，保留鉴权头。
- 领域 API 辅助函数放在 `lib` 下的领域 `.js` 文件（如 `courseware.js`）并复用通用 `api`，不要在模板中直接散落 fetch。

## 18. JavaScript 规范

- ERP 前端统一使用 JavaScript：Vue 文件写 `<script setup>`，脚本文件使用 `.js`，禁止新增 `.ts`、`.tsx`、`interface`、`type`、`enum`、泛型和类型注解。
- 当前 ERP 不保留 TypeScript 配置、`vue-tsc` 类型检查脚本或 `env.d.ts`；构建验证使用 `npm run build`。
- API envelope 和分页结构通过运行时约定保持与后端一致：`{ code, message, data }` 以及 `{ list, page, size, total }`。复杂返回形状在 API 辅助函数和页面边界处清楚命名，不引入重复类型层。
- 有限状态使用字符串常量或已有格式化字典，不使用 TypeScript enum；共享字典放在 `lib` 下。
- 表单和 API 数据通过 `ref`、`reactive`、默认值和必要的运行时判断保证稳定性；不要用伪类型注释替代校验。
- 使用 `@/` 别名导入 `src`；导入顺序保持 Vue、第三方库、项目模块的清晰分组。

## 19. CSS / UI 规范

- 使用 Element Plus 作为 UI 组件库；入口在 `main.js` 引入 Element Plus 中文 locale 和 CSS。
- 全局样式位于 `assets/base.css` 和 `assets/main.css`；组件页面使用 `<style scoped>`，确需覆盖 Element Plus 时使用 `:deep` 或 `:global`。
- 参考视觉变量包括 `--brand`、`--ink`、`--muted`、`--line`、`--canvas`、`--surface`、`--danger`、`--warning`、`--success`；新增颜色优先复用变量。
- 页面以 `page`、`page-head`、`panel`、`filter-bar`、`panel-body`、`table-footer`、`empty-state` 等语义类组织；类名使用 kebab-case。
- 组件卡片和 Dialog 使用约 8px 圆角；表格表头、悬停色、表单间距沿用现有变量和 Element Plus 覆盖。
- 布局使用 CSS Grid/Flex 和明确的响应式断点；参考项目在 1100px、900px、700px 等宽度下调整列数、抽屉和表单。
- 不在业务页面引入 Tailwind、CSS Modules、Less/SCSS 或第二套设计系统，除非项目整体先完成明确迁移决策。

## 20. 注释规范

- 参考项目几乎没有类、方法、字段 Javadoc；业务代码默认不添加模板化注释。
- 只在非显然的算法、外部系统约束、兼容性处理、资源清理策略前写一行简短说明。参考实例包括“清理是 best effort”和导出日期回退逻辑。
- 不为 getter、setter、简单 CRUD 或每一行代码添加注释；不写重复代码含义的注释。
- `TODO`、`FIXME` 只用于已确认的后续工作，并写出具体问题；避免留下没有上下文的占位文本。
- SQL 业务意图可通过 Mapper 方法名、缩进和必要的 SQL 注释表达；不要在生产代码中遗留调试输出。

## 21. Git / Commit 规范

参考项目没有可供统计的提交历史规范，因此当前采用最小约束：

- 每次提交聚焦一个可说明的变更，标题使用简短动词和范围，例如 `feat(inventory): add stock query api`、`fix(auth): handle expired token`、`docs: update coding conventions`。
- 不把格式化、依赖升级、业务实现和无关重构混在同一提交。
- 提交说明写明行为变化和验证命令；若偏离本规范，写出原因。
- 不提交 `target/`、`node_modules/`、`dist/`、日志、IDE 文件、密钥和本地配置。沿用各项目现有 `.gitignore`。

## 22. 测试规范

- 后端使用 Spring Boot Test、JUnit 5、MockMvc、Mockito；参考项目同时有 Controller 测试、Service 单元测试、MySQL 集成测试和静态前端路由测试。
- Controller 测试重点验证 HTTP 方法、路径、参数校验、统一成功/失败 envelope 和权限边界；Service 单测重点验证状态冲突、唯一性和删除保护。
- 涉及真实 SQL、事务、锁或数据库约束时使用 `@SpringBootTest` 和 `@Transactional` 集成测试；测试结束清理 `CurrentUserContext` 等 ThreadLocal。
- 参考项目使用 Playwright E2E，配置位于 `tests/e2e` 和 `playwright.config.ts`；当前 ERP 尚未配置 E2E 测试，后续如增加必须使用 JavaScript 配置并覆盖登录、权限、主要管理流程、移动端布局和 API 合同。
- 修改共享返回结构、鉴权、分页、Mapper 或跨模块事务时扩大验证范围；纯文档、低风险样式和局部可逆改动不强制新增镜像测试。
- 常用验证：后端运行 Maven 测试，前端运行 `npm run build`；当前没有 TypeScript 类型检查脚本。需要浏览器流程时，运行项目实际配置的 E2E 命令。测试依赖的数据库、MinIO、KKFileView 等外部服务必须在结果中注明。

## 参考项目技术摘要

- 后端：Spring Boot 4.1.0、Java 17、Spring MVC、MyBatis Spring Boot 4.0.0、MySQL Connector/J、Validation、Spring Security Crypto、Lombok；文件/导出使用 MinIO、Apache POI。
- 参考前端：Vue 3.5、TypeScript 6、Vite 8、Vue Router 5、Element Plus 2、Element Plus Icons；请求为原生 `fetch`，上传特殊使用 `XMLHttpRequest`。当前 ERP 按用户明确要求使用 Vue 3 + JavaScript + Vite + Vue Router + Element Plus + 原生 `fetch`，不复制参考项目的 TypeScript。
- 数据库：MySQL 8.0/InnoDB/utf8mb4；数据库维护时间字段、唯一键、外键、检查约束和索引。
- 当前 ERP 初始技术基线为 Spring Boot 4.1.1 + Java 17，以及 Vue 3/Vite/JavaScript 模板；新增依赖必须有需求依据。
