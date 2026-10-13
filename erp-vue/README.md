# erp-vue

ERP 管理系统前端，使用 Vue 3、JavaScript、Vite、Vue Router、Element Plus 和原生 `fetch`。

## 开发环境

需要 Node.js 22+ 和 npm。

## 安装依赖

```powershell
npm install
```

## 开发启动

```powershell
npm run dev
```

开发服务器默认监听 `http://localhost:5173`，`/api` 和 `/health` 通过 Vite 代理转发到后端。

## 生产构建

```powershell
npm run build
```

当前前端统一使用 JavaScript，不创建 TypeScript 文件，也没有独立的 TypeScript 类型检查命令。
