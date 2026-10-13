# 参考代码索引

本索引记录参考项目 `ems` / `ems-vue` 中已经沉淀到本项目规范的写法。参考目录仅用于历史对照和本地可选核查，可能不随 ERP 备份提供；开发时以 [`coding-conventions.md`](coding-conventions.md)、`AGENTS.md` 和 ERP 自身代码为准。这些文件只用于观察“怎么写”，不表示 ERP 要复制其中的教务、课件、用户或排课业务。

下列参考路径只在本地保留参考副本时可读取，不是 ERP 开发或构建的依赖。没有参考副本时，按 `coding-conventions.md` 的明确规范和 ERP 中现有实现继续开发。

## 后端

参考 Controller：

- `ems/src/main/java/com/haucheng/ems/controller/UserController.java`：标准分页、详情、创建、更新、状态、删除和权限注解。
- `ems/src/main/java/com/haucheng/ems/controller/CoursewareController.java`：文件上传、下载重定向、列表和 multipart 参数。
- `ems/src/main/java/com/haucheng/ems/controller/ScheduleController.java`：普通 CRUD 与动作型接口（完成、取消、恢复、代课、补课）。
- `ems/src/main/java/com/haucheng/ems/controller/TimetableController.java`：JSON 查询与 Excel `ResponseEntity<byte[]>` 导出例外。

参考 Service：

- `ems/src/main/java/com/haucheng/ems/service/CampusService.java`：直接 Service 类、手工分页、规范化字符串、唯一性和删除前引用校验。
- `ems/src/main/java/com/haucheng/ems/service/UserService.java`：多 Mapper 协作、事务、权限/角色业务校验和敏感操作。
- `ems/src/main/java/com/haucheng/ems/service/AuthService.java`：Bearer Token、当前用户、事务和安全密码处理。
- `ems/src/main/java/com/haucheng/ems/service/SessionService.java`：复杂状态流转、锁定读取和批量动作。
- `ems/src/main/java/com/haucheng/ems/service/CoursewareService.java`：外部 MinIO、资源清理、文件名规范化和 VO 转换。

参考 Entity：

- `ems/src/main/java/com/haucheng/ems/entity/SysUser.java`：普通 MyBatis Entity + `@Data` + 默认状态。
- `ems/src/main/java/com/haucheng/ems/entity/ClassSession.java`：时间字段、数量、状态和类型默认值。
- `ems/src/main/java/com/haucheng/ems/entity/Campus.java`：简单资源 Entity、审计时间字段。

参考 DTO：

- `ems/src/main/java/com/haucheng/ems/dto/UserCreateRequest.java`：`@Data`、字段校验、集合元素校验。
- `ems/src/main/java/com/haucheng/ems/dto/BatchScheduleRequest.java`：多 ID、日期时间、数值范围和中文校验消息。
- `ems/src/main/java/com/haucheng/ems/dto/StatusUpdateRequest.java`：局部状态修改请求的最小结构。
- `ems/src/main/java/com/haucheng/ems/dto/RoleRequest.java`：`@Pattern` 和编码约束。

参考 VO：

- `ems/src/main/java/com/haucheng/ems/vo/UserView.java`：对外用户视图，不含密码哈希。
- `ems/src/main/java/com/haucheng/ems/vo/SessionView.java`：联表后的详情/列表视图。
- `ems/src/main/java/com/haucheng/ems/vo/SessionDetailView.java`：主对象 + 变更列表的详情包装。
- `ems/src/main/java/com/haucheng/ems/vo/WeeklyTimetableView.java`：`@AllArgsConstructor` 聚合结果和嵌套列表。
- `ems/src/main/java/com/haucheng/ems/vo/MenuView.java`：树结构 `children` 默认空列表。

参考 Mapper：

- `ems/src/main/java/com/haucheng/ems/mapper/UserMapper.java`：`@Select` 文本块、动态 `<where>`、分页、count、generated keys 和 `@Param`。
- `ems/src/main/java/com/haucheng/ems/mapper/CampusMapper.java`：标准资源 CRUD、选项查询和教学关系查询。
- `ems/src/main/java/com/haucheng/ems/mapper/TermMapper.java`：文本块动态 SQL、`FOR UPDATE`、日期条件和 generated keys。
- `ems/src/main/java/com/haucheng/ems/mapper/SessionMapper.java`：复杂联表、批量查询、锁定读取和状态筛选。
- `ems/src/main/java/com/haucheng/ems/mapper/RoleMapper.java`：`<foreach>` 集合参数。

参考通用/异常/配置：

- `ems/src/main/java/com/haucheng/ems/common/ApiResponse.java`：统一 envelope 和 success/error 工厂方法。
- `ems/src/main/java/com/haucheng/ems/common/PageResult.java`：分页返回结构。
- `ems/src/main/java/com/haucheng/ems/common/Pagination.java`：默认/最大分页参数和 offset。
- `ems/src/main/java/com/haucheng/ems/exception/BusinessException.java`：业务异常状态码工厂。
- `ems/src/main/java/com/haucheng/ems/exception/GlobalExceptionHandler.java`：参数、数据库、资源和未知异常统一处理。
- `ems/src/main/java/com/haucheng/ems/auth/RequirePermission.java`：类型/方法级权限元注解。
- `ems/src/main/java/com/haucheng/ems/auth/AuthInterceptor.java`：Bearer Token、权限和 ThreadLocal 清理。
- `ems/src/main/java/com/haucheng/ems/config/WebConfig.java`：拦截器注册和 SPA 路由转发。
- `ems/src/main/java/com/haucheng/ems/config/MinioConfig.java`：`@ConfigurationProperties`、`@Bean` 和 Lombok。
- `ems/src/main/resources/application.properties`：环境变量占位、数据库、文件服务和上传限制配置。

## 前端

参考 Vue 页面：

- `ems-vue/src/views/TeachersView.vue`：典型列表/筛选/分页/卡片/编辑 Dialog、权限按钮和请求状态。
- `ems-vue/src/views/ResourcesView.vue`：Tab、多个资源列表和嵌套编辑流程。
- `ems-vue/src/views/CoursewareView.vue`：文件夹、上传进度、预览、下载、空/错/loading 状态。
- `ems-vue/src/views/StatisticsView.vue`：统计数据、computed 派生值和响应式筛选。
- `ems-vue/src/views/SystemView.vue`：用户、角色、权限树和 Drawer 编辑。
- `ems-vue/src/views/LoginView.vue`：登录表单和路由跳转。

参考 Vue 组件：

- `ems-vue/src/components/ChangePasswordDialog.vue`：类型化 Props/Emits、FormRules、watch 重置和 Dialog。
- `ems-vue/src/components/ScheduleDialog.vue`：多步骤表单、computed、watch、批量提交和局部状态。
- `ems-vue/src/components/SessionDetailDialog.vue`：详情编辑、动作表单和错误处理。
- `ems-vue/src/components/WeekScheduleEditor.vue`：`withDefaults(defineProps(...))`、类型化事件和可编辑网格。
- `ems-vue/src/layouts/AppLayout.vue`：响应式布局、权限过滤导航、路由和 resize 生命周期清理。

参考 API/类型/辅助：

- `ems-vue/src/lib/api.ts`：fetch 请求封装、Token 注入、统一 envelope 错误和下载。
- `ems-vue/src/lib/auth.ts`：响应式用户状态、restore/login/logout 和权限判断。
- `ems-vue/src/lib/courseware.ts`：领域 API 函数、上传进度和文件下载。
- `ems-vue/src/lib/types.ts`：`ApiEnvelope<T>`、`PageResult<T>`、领域 `interface` 和联合字面量。
- `ems-vue/src/lib/format.ts`：日期、时间、状态字典和简单格式函数。
- `ems-vue/src/router/index.ts`：懒加载页面、路由 meta 权限和全局登录守卫。
- `ems-vue/src/main.ts`：Element Plus、中文 locale、全局 CSS、Router 挂载顺序。
- `ems-vue/src/assets/base.css`：颜色变量、字体、基础元素和 Element Plus 主题变量。
- `ems-vue/src/assets/main.css`：页面、面板、筛选条、表格、空状态和响应式布局类。

## 测试参考

- `ems/src/test/java/com/haucheng/ems/controller/CampusControllerTest.java`：MockMvc 参数校验、成功分页和业务冲突。
- `ems/src/test/java/com/haucheng/ems/controller/CoursewareControllerTest.java`：multipart、下载 302、统一错误和兼容接口。
- `ems/src/test/java/com/haucheng/ems/service/UserServiceDeleteTest.java`：Mockito Service 单测和当前用户上下文清理。
- `ems/src/test/java/com/haucheng/ems/service/AcademicFlowIntegrationTest.java`：MySQL 事务集成流程。
- `ems/src/test/java/com/haucheng/ems/SpaRoutingIntegrationTest.java`：嵌入式前端与 `/api` 隔离。
- `ems-vue/tests/e2e/smoke.spec.ts`：Playwright 登录、管理区域和移动端冒烟。
- `ems-vue/tests/e2e/system-permissions.spec.ts`：权限树行为。
- `ems-vue/playwright.config.ts`：E2E 目录、Vite dev server、baseURL 和超时。

## 参考项目范围中的潜在问题（仅记录）

- 参考 `application.properties` 含有开发环境数据库、MinIO 和默认账号密码；ERP 必须通过环境变量或部署密钥管理实际凭据，不能复制默认敏感值。
- 参考项目没有 Swagger/OpenAPI 文档、统一 Java 格式化/静态检查配置；后续若引入工具应先形成项目级决策，不要在单个模块偷偷改变风格。
- 个别 Vue 页面和 CSS 采用很长的单行表达式，页面文件较大；这是现状证据，不要求 ERP 复制不可读写法，新代码应在不改变整体风格的前提下保持可维护分段。
- 参考项目依赖真实 MySQL、MinIO、KKFileView 的集成测试环境；运行测试时需要记录外部服务前提，不能把环境缺失误判为代码通过。
- 参考项目存在业务状态字符串和手工校验，未集中为统一枚举；ERP 可沿用，但同一业务内必须保持一种稳定表示并在偏离时说明。
