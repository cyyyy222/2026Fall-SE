# 实验三开发文档入口

项目：活动票务与报名系统。文档日期：2026-11-02。

当前状态：**开发契约v0.2及桌面网页前端演示已落盘；Java后端与SQLite尚未实现，尚未进行完整业务、性能或故障验收**。本目录文档是工程设计材料，不是实验报告正文。

前端入口：[运行说明与演示账号](code/frontend/README.md)；[页面覆盖和验证记录](docs/frontend-delivery.md)。在`phase3/code/frontend`运行`npm.cmd ci`及`npm.cmd run dev`，打开终端显示的本地地址。只构建桌面浏览器网页，不做手机应用。

推荐阅读顺序：

1. [前后端开发项目文档](docs/project.md)：范围、技术方案、模块、页面、状态、存储、运行规划与开发顺序。
2. [接口约定](docs/api.md)：统一协议、字段模型、接口目录、权限、幂等及异常处理。
3. [需求响应路径与验收追踪](docs/requirements-traceability.md)：REQ-001～REQ-053 从页面到接口、模块、持久化和验收的逐条对应。
4. [v0.2 语义复核记录](docs/review-v0.2.md)：本次发现的问题、修订依据及尚需实际实现验证的边界。

需求依据为 [phase1 version5](../phase1/my_exp_cont/requirements.md) 正文及附录 A～D；结构依据为 [phase2 UML](../phase2/UML/)；视觉依据为 [phase2 UI 注记](../phase2/UI界面/UI设计清单与交互注记.md)。历史访谈与旧方案不作为并行执行的需求。

本次约定 Java / Spring Boot 后端、Vue 3 / TypeScript / Vite 前端、SQLite 本地存储；开发时分开运行，交付时前端构建结果由 Java 应用提供。页面允许按实际流程重组，沿用已经确认的莫兰迪视觉风格。

前端目录、启动命令和页面已实现；Java/SQLite相关接口与服务仍是后续目标。前端本地模拟不代表正式后端已完成，业务调整应同步修改契约与追踪表。
