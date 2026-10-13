# ERP 项目 AI 开发规则

当前仓库是全新的 Vue 3 + Spring Boot 4 企业内部物资、库存、设备管理系统。参考项目为 `ems` / `ems-vue`，只用于代码风格和工程结构参考。参考项目中的教务、用户、课件、排课等业务不能自动迁移到 ERP。

## 必须遵守

1. 后续 Codex 修改本项目时，必须先阅读并遵守 [`docs/coding-conventions.md`](docs/coding-conventions.md)。
2. 需要模板时，先查阅 [`docs/reference-code.md`](docs/reference-code.md) 了解对应模式，再以 `docs/coding-conventions.md` 和 ERP 自身代码为实现依据。`ems/`、`ems-vue/` 仅是本地可选参考项目，可能不在 ERP 备份中；不得把它们当作开发依赖，也不得复制其业务。
3. 优先复用现有代码模式，不要无理由引入新架构。
4. 不要无理由更换依赖、数据库访问技术、UI 组件库或请求封装。
5. 不要改变既有目录结构；新增目录必须有职责和使用场景。
6. 不要改变命名风格、文件后缀和包职责边界。
7. 不要改变注解风格；后端沿用 Controller/Service/Mapper、MyBatis 注解、Lombok 和 Jakarta Validation 的现有模式。
8. 不要改变统一返回结构；JSON API 默认使用 `ApiResponse<T>`，分页使用 `PageResult<T>`。
9. 不要改变异常处理方式；业务错误使用 `BusinessException`，由 `GlobalExceptionHandler` 统一输出。
10. 不要随意增加第三方库；新增依赖必须在改动说明中说明用途、替代方案和影响。
11. 不要为了“最佳实践”而重构现有代码；只在当前需求、缺陷修复或明确的技术债范围内修改。
12. 当前阶段不要创建库存、设备、领用、审批或其他业务功能文件，除非用户明确开始该功能阶段。

## 实现边界

- 后端默认使用 `com.haucheng.erp` 根包、`controller`、`service`、`mapper`、`entity`、`dto`、`vo` 等参考目录。
- Service 默认是具体类，不创建空接口和 `service.impl`。
- Mapper 默认使用 MyBatis 注解 SQL，分页使用 `Pagination` + `LIMIT/OFFSET`。
- Vue 默认使用 `<script setup>`、JavaScript、Element Plus、Vue Router 和 `lib/api.js` 的原生 fetch 封装；ERP 禁止新增 `.ts`、`.tsx` 文件和 TypeScript 语法。
- 没有真实复用需求时，不新增 Pinia、Vuex、axios、JPA、MyBatis-Plus、Swagger/OpenAPI、Tailwind 或另一套 UI 库。
- 不复制参考业务的实体、菜单、权限、数据库表、页面或 API；只提取“怎么写”。

## 偏离规范

如果需求确实需要偏离 `docs/coding-conventions.md`，必须在实现前后的说明中写清楚：

- 偏离的具体条款和文件范围；
- 为什么现有模式无法满足需求；
- 对返回结构、异常、依赖、目录和后续维护的影响；
- 如何保证兼容性和测试覆盖。

除非用户明确要求，不得借偏离机会顺便重构无关模块、升级依赖或修改参考项目。

## 变更流程

1. 先读取当前实现、规范和参考索引，确认是否已有可复用模式。
2. 先做最小实现，再运行与风险匹配的测试和类型检查。
3. 涉及共享 API、鉴权、分页、Mapper、事务或配置时扩大验证范围。
4. 最终说明改了什么、为什么这样写、运行了哪些验证，以及任何外部服务前提或未解决风险。
