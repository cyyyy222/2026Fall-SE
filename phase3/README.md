> 2026-11-03最新规则与验证：[注册、验证码找回、详细申诉与个人信息换绑](docs/account-recovery-update.md)。邮箱选填；换绑只验证新号，并校验同角色占用；后端58项、前端22项回归通过，真实联调范围见该文。该说明优先于下文旧版相关约束与早期验证记录。

# 实验三开发文档入口

项目：活动票务与报名系统。文档日期：2026-11-02。

当前状态：**开发契约v0.6及桌面网页已落盘；Java三层业务已接通本机MySQL/Redis，真实演示数据已导入，前端切换HTTP，后台任务按用户授权启用。后端58项、前端22项测试通过，真实HTTP与服务事务集成范围见最新交付；完整需求、性能和故障恢复尚未全部验收。** 本目录文档是工程设计材料，不是实验报告正文。

2026-11-03更新：[验证码登录注册、手机号规则和管理员账号分类说明](docs/account-auth-update.md)。

当前运行入口：[HTTP演示数据、账号与启动说明](docs/http-demo.md)。打开http://127.0.0.1:5173/#/login，主参与者participant_demo / DemoPass123。旧mock账号、固定详情UUID和浏览器存储不对应真实数据库。

前端入口：[运行说明与演示账号](code/frontend/README.md)；[页面覆盖和验证记录](docs/frontend-delivery.md)。在`phase3/code/frontend`运行`npm.cmd ci`及`npm.cmd run dev`，打开终端显示的本地地址。只构建桌面浏览器网页，不做手机应用。

后端本轮入口：[业务初版交付与待接入信息](docs/backend-implementation.md) · [运行说明](code/backend/README.md) · [89项接口映射](docs/backend-endpoints.md)。按最新授权实现了可独立编写的业务逻辑，本机数据库及Redis现已接通，接入记录与启动命令见[本机接入说明](docs/local-connection.md)；本工具没有执行建表SQL。

推荐阅读顺序：

1. [前后端开发项目文档](docs/project.md)：范围、技术方案、模块、页面、状态、存储、运行规划与开发顺序。
2. [接口约定](docs/api.md)：统一协议、字段模型、接口目录、权限、幂等及异常处理。
3. [需求响应路径与验收追踪](docs/requirements-traceability.md)：REQ-001～REQ-053 从页面到接口、模块、持久化和验收的逐条对应。
4. [v0.2 语义复核记录](docs/review-v0.2.md)：本次发现的问题、修订依据及尚需实际实现验证的边界。

需求依据为 [phase1 version5](../phase1/my_exp_cont/requirements.md) 正文及附录 A～D；结构依据为 [phase2 UML](../phase2/UML/)；视觉依据为 [phase2 UI 注记](../phase2/UI界面/UI设计清单与交互注记.md)。历史访谈与旧方案不作为并行执行的需求。

本次约定 Java / Spring Boot 后端、Vue 3 / TypeScript / Vite 前端、MySQL业务持久化及Redis短期辅助服务；开发时分开运行，交付时前端构建结果由 Java 应用提供。页面允许按实际流程重组，沿用已经确认的莫兰迪视觉风格。

前端与Java业务已完成本机垂直联调，剩余全接口、并发与故障验收见最新交付说明；业务调整应同步修改契约与追踪表。
