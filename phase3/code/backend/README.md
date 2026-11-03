> 2026-11-03最新规则与验证：[注册、验证码找回、详细申诉与个人信息换绑](../../docs/account-recovery-update.md)。邮箱选填；换绑只验证新号，并校验同角色占用；后端58项、前端22项回归通过，真实联调范围见该文。该说明优先于下文旧版相关约束与早期验证记录。

# Java 后端业务初版

Java17 / Spring Boot3.5.16 / Maven，Controller → Service → DAO，MySQL + Redis。

91项业务接口已接入具体用例，核心逻辑和参数化DAO已编写。**本机MySQL/Redis已接通，演示数据已导入，前端已切HTTP；后台任务已按用户授权启用。** 账号、数据、真实9组流程联调结果见[HTTP演示说明](../../docs/http-demo.md)。后端53项测试通过，不代表全部需求、并发和故障场景已验收。

2026-11-03：已加入手机号验证码登录及注册校验，验证码在本地Java终端输出；可用`Get-Content backend-local.log -Tail 20 -Wait`查看。验证码规则、账号四类管理、凭据交接与本次索引迁移见[账号与登录调整](../../docs/account-auth-update.md)。

## 在VS Code中运行

```powershell
cd D:\2026Fall-SE\phase3\code\backend
$env:JAVA_HOME='D:\javajdk17'
mvn.cmd test
mvn.cmd package
& "$env:JAVA_HOME\bin\java.exe" -jar target/ticketing-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local-connected --app.jobs.enabled=true --logging.file.name=backend-local.log
```

本机已验证JDK17.0.17 / Maven3.9.4；也可用项目`mvnw.cmd test`、`mvnw.cmd spring-boot:run`。首次构建需下载依赖，项目设置使用官方中央仓库、本目录`.m2`缓存。打包后也可用JDK17运行`java -jar target/ticketing-backend-0.1.0-SNAPSHOT.jar`。

默认监听127.0.0.1:8080。上述连接模式健康应为200、MySQL/Redis均UP；需先运行本机两个服务并保留被忽略的`application-local-private.yml`。前端另开终端在`../frontend`运行`npm.cmd run dev`，访问5173。

不指定profile运行时仍是离线模式。以下仅为离线配置的预期行为：

| 地址/动作 | 结果 |
| --- | --- |
| GET /api/auth/csrf | 200，真实token与Cookie |
| GET /api/system/health | 503，NOT_READY，MySQL/Redis=NOT_CONNECTED |
| GET /api/public/sessions | 503 DATABASE_UNAVAILABLE，业务需要可靠存储 |
| GET /api/me/orders（未登录） | 401 AUTH_REQUIRED |
| 缺CSRF的POST | 403 CSRF_INVALID |
| 输入非法/拼错查询参数 | 400或422，按api.md |

不会用空列表或内存数据伪装数据库已接好。当前前端本机`.env.local`为HTTP，真实操作写入MySQL；旧mock账号与数据不自动迁移。

## 代码结构

| 目录 | 责任 |
| --- | --- |
| controller | HTTP/DTO转换，保留89业务路径及SYS-01 |
| service/impl | 强类型Service门面 |
| service/core | 业务规则、授权、事务、幂等与消息日志、持久任务编排 |
| dao/RecordStore、dao/jdbc | 固定33实体注册表、封闭条件/排序、参数化SQL与记录映射 |
| dao/redis | 明确key及Lua窗口计数，禁止作为业务权威 |
| dto、entity、enums | 请求/查询/响应与内部持久记录分开 |
| security、filter、interceptor | MySQL会话/CSRF、trace、角色、请求约定和服务端上下文 |
| scheduler | 默认关闭的持久任务扫描入口 |
| src/test | 入口契约、规则/用例、DAO/Redis适配器检查；夹具仅在测试目录 |

此前的XxxDao端口仍保留；当前业务使用RecordStore/JdbcRecordStore作为实际DAO，不宣称已有33个专用JdbcXxxDao。准备阶段生成器已冻结并主动拒绝运行，避免覆盖真实ServiceImpl；现在直接维护Java源文件。

## 默认配置与显式开发模式

application.yml保持connections.enabled=false、jobs.enabled=false、flyway.enabled=false、sql.init.mode=never。application-connected.yml只是后续连接模板，不包含实际凭据，也不自动建表。

通用connected模板使用MYSQL_URL/USERNAME/PASSWORD、REDIS_HOST/PORT/PASSWORD；认证限流需要APP_RATE_HMAC_SECRET（至少32字节）。本机local-connected已从忽略文件读取，不要把秘密写入仓库。本地受控文件默认data，可用APP_DATA_DIR指定。部署HTTPS时设置APP_COOKIE_SECURE=true。只信任实际TCP远端地址，未配置可信反向代理。

本机33表结构已核验；没有执行DDL或自动迁移。当前后台任务通过启动参数显式启用。演示导入器仅在显式传入app.demo.seed.enabled=true时启用，只允许本机campus_ticketing_dev；重复不覆盖数据。已经导入，无需平时重导。初始管理员admin_demo须改密，这只是开发库初始化，正式部署的管理员引导仍需单独设计。命令与账号见[HTTP演示说明](../../docs/http-demo.md)。

## 主要设计材料

[API契约](../../docs/api.md) · [33表/完整DDL](../../docs/database-design.md) · [Redis命名和TTL](../../docs/redis-design.md) · [Java参数映射](../../docs/backend-contracts.md) · [当前业务交付](../../docs/backend-implementation.md)。

真实购票/支付/退款/检票垂直流程已通过；Redis基础Lua/TTL已单独核验。全部MySQL约束/隔离、并发/宕机恢复、EXPLAIN与性能尚未完整验收。
