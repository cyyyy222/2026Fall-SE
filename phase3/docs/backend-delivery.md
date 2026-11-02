> 此文为上一轮准备阶段记录，已完成用户审阅。最新状态见[后端业务初版交付](backend-implementation.md)，其中列出当前实现、配置缺口和待验收事项。

# 后端准备工作审阅入口

日期：2026-11-02。当前交付 **Java三层骨架与完整准备材料**；没有连接MySQL/Redis、没有执行建库建表、没有实现报名/支付/退款等核心业务。

## 建议审阅顺序

1. [自审与修订记录](backend-review.md)：发现的问题、修改及尚未验证的边界。
2. [MySQL数据库设计](database-design.md)：33张表、每个字段的类型/空值/含义、外键、唯一/检查约束、查询索引，文末包含完整建表命令。[独立SQL审阅稿](mysql-schema.sql)内容相同。
3. [Redis分类与键字典](redis-design.md)：哪些要用、哪些延后、哪些只能放MySQL；key模板、命名维度、值字段、TTL、阈值及故障策略。
4. [三层实施方案](backend-plan.md)：职责、安全链、锁顺序、事务边界、默认离线配置及后续实施顺序。
5. [89项接口三层映射](backend-endpoints.md)与[Java参数/返回模型映射](backend-contracts.md)：对照[api.md](api.md)审阅Controller、Service、Requests/Queries/Views；原桌面前端业务字段未改。
6. [后端运行说明](../code/backend/README.md)：在VS Code终端构建、启动及预期响应。

## 工程准备范围

工程位于 `phase3/code/backend`，JDK17 / Spring Boot 3.5.16 / Maven。已有Controller、Service接口/Impl、DAO端口/JDBC及Redis基础适配器、33个Entity、请求/查询/响应模型、枚举、统一异常和JSON、trace、安全/CSRF、请求约定拦截器、PATCH三态与输入校验、7项内部任务边界和配置。

DAO端口包含按业务键查找、版本更新、占位计数、当前授权、可靠请求映射和任务待办的准备签名；逐表Jdbc实现、RowMapper及真实事务留待核心阶段。默认无数据库/Redis客户端，无自动迁移、管理员初始化或自动调度。

89项业务路由中，AUTH-01是真实CSRF基础设施，其余88项明确501。角色/CSRF/输入检查仍生效；真实会话未实现，不能使用前端mock账号登录Java。SYS-01单列，默认503 NOT_READY且组件NOT_CONNECTED，是预期的离线审阅状态。

## 验证与限制

已用JDK17.0.17 / Maven3.9.4构建，Boot3.5.16依赖可解析；标准Maven Wrapper固定Maven3.9.9，已实际运行-version验证。最终package于2026-11-02 16:43通过。框架契约测试12项通过（0失败、0错误），包括：

- api.md中的89项方法/路径/编号与实际Spring路由一致，无缺失或重复。
- 默认配置无DataSource、RedisConnectionFactory或Flyway，不会自动接库/建表。
- 真实CSRF Cookie/token交换；缺CSRF、匿名私有访问、角色错误、强改密白名单及业务501。
- 未知/重复查询参数、分页范围、严格布尔值、写入未知字段及客户端注入金额被拒绝。
- PATCH省略/null/值三态、必现可空字段、Unicode码点长度、北京时间偏移/整点和严格整数类型。
- 离线健康状态与Redis key环境/指纹格式。

可执行JAR已在127.0.0.1:18080启动并完成7项真实HTTP检查：CSRF200、健康503、公开占位501、匿名私有401、拼错查询422、缺CSRF403、真实Cookie/token登录占位501；验证后停止该测试进程。正式默认端口仍为8080。受限执行环境阻止Java回环连接和测试附加管道，因此实际启动与最终构建在经授权的本机权限下验证；这不是MySQL/Redis连接验收。MySQL DDL尚未执行，Redis Lua尚未实际运行，真实会话/事务/并发/恢复/性能和前后端HTTP业务联调均未验收；不能把上述离线测试当成53项需求已完成。

## 本次审阅重点

确认三层结构、33表职责和字段、历史保留边界、Redis范围、DTO命名与前端契约，以及后续顺序是否符合预期。本轮在此停留供审阅；下一阶段再从独立数据库连接/DDL验证、真实身份与限流基础链开始，然后实现活动和交易核心。
