# 本机真实 HTTP 联调与演示数据

日期：2026-11-02。本机前端已切换 HTTP；Java 使用 `local-connected` 连接 `campus_ticketing_dev` 与本机 Redis。后台任务已按用户明确授权启用。此文是当前运行说明，早期文档中的“前端保持 mock / 业务尚未接库”属于历史阶段。

## 打开与运行

当前 Vite 5173、Java 8080 均已运行。打开 **http://127.0.0.1:5173/#/login**。如果之前登录过浏览器 mock 账号，刷新后用下表真实账号重新登录。

以后重新启动，先启动本机 MySQL 和 Redis，再在 VS Code 开两个终端：

```powershell
# 终端一：后端；从此目录运行，才能加载被 Git 忽略的本机配置
cd D:\2026Fall-SE\phase3\code\backend
$env:JAVA_HOME='D:\javajdk17'
& "$env:JAVA_HOME\bin\java.exe" -jar target/ticketing-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local-connected --app.jobs.enabled=true
```

```powershell
# 终端二：前端；已有 node_modules 时无需重新 npm ci
cd D:\2026Fall-SE\phase3\code\frontend
npm.cmd run dev
```

后端代码改动后先运行 `mvn.cmd package`（或 `mvnw.cmd package`），再启动新 JAR。端口已被当前服务使用时不要重复启动；先在旧终端 Ctrl+C。使用同一个浏览器来源 `127.0.0.1:5173`。

前端已创建本机 `.env.local`：

```dotenv
VITE_API_MODE=http
VITE_DEMO_ACCOUNTS=true
```

`VITE_DEMO_ACCOUNTS` 只展示开发登录快捷填充，不改变身份校验；正式部署应关闭。配置变化需要重启 Vite。`/api` 代理到 `127.0.0.1:8080`，HTTP 失败会显示真实错误，不回退 mock。原浏览器 localStorage 演示数据没有迁入数据库，也不会成为 HTTP 业务数据来源。前端构建前也应设置所需模式；Vite 环境变量会编入构建产物。

## 真实开发账号

以下是专供本机开发库的演示凭据，数据库存 BCrypt 哈希。普通演示账号密码均为 **DemoPass123**。

| 用户名 | 用途 |
| --- | --- |
| participant_demo | 主参与者，保留干净状态供手动购票 |
| organizer_demo | 组织者，查看和维护全部示例活动 |
| participant_ticket_demo | 持有满座场次、现场分享会的电子票 |
| participant_wait_demo | 满座场次候补；前一位退票可观察递补 |
| participant_pending_demo | 工坊待支付示例；超时后后台会取消 |
| participant_history_demo | 历史缺席与平台退款示例 |
| participant_test_demo | HTTP 自动联调专用，已有测试退票与历史签到 |
| checker_demo | 音乐会检票员 |
| checker_workshop_demo / checker_free_demo / checker_full_demo | 分别负责工坊、公益跑、满座小剧场 |
| checker_live_demo | 正在进行的分享会检票员 |
| checker_review_demo | 待审核交流会检票员，已绑定供审核检查 |
| admin_demo | 管理员，初始密码 **AdminDemo123**；首次登录必须修改密码 |

另有 `checker_history_demo` 与 `checker_cancelled_demo` 两个已逻辑删除的历史检票员，用于保留历史关系，不能登录。共 16 个账号。管理员改密后快捷按钮仍填初始口令，应手动输入自己的新密码；重复导入不会重置密码。

这些账号不同于旧 mock 的 `participant / organizer / checker / admin`、密码 `Demo2026`。普通注册、管理员授权等新建的账号也会真实持久化。

## 数据内容与阅览路径

首次显式导入在一个 MySQL 事务内创建 5 个类别、9 个活动、7 个订单，并写入对应场次、票种、检票员授权、票、支付退款记录、候补、消息、举报调查、签到、缺席、统计快照、任务及审计事实。实际联调又产生测试订单、退款和签到，因此当前行数会随操作变化。

| 活动 | 初始状态与用途 |
| --- | --- |
| 秋日校园音乐会 | 已发布，标准票 39 元、学生票 19 元，可用主参与者报名→模拟支付→下载文本票→退款 |
| 手作与香气·周末工坊 | 已发布，小容量票种与待支付超时示例 |
| 校园公益跑·一起出发 | 已发布，免费报名直接出票 |
| 小剧场·满座与候补 | 容量 1，已有一张票及一位候补；先退票再查看候补递补 |
| 城市影像分享会·正在进行 | 初次导入所在整点起持续 2 小时，供真实签到；联调已签到一次，可验证重复签到返回原时间 |
| 校园策展人·主题交流会 | 待审核，已分配检票员，可由管理员改密后审核 |
| 我的下一场活动·草稿 | 不完整草稿，供组织者继续编辑 |
| 两个历史活动 | 分别为结束活动与取消活动，提供签到/缺席/退款/终止任务与历史统计 |

日期以首次导入时的服务端时间为基准：未来活动为后 1～5 天，历史活动为前一天。现场场次实际窗口是 **2026-11-02 20:00～22:00（北京时间）**。过期后不能靠重导改变历史，应正常新建未来/现场活动并配置检票员。待支付示例的 15 分钟期限及候补资格也会随真实时间和任务变化。活动开场前不能签到是正常业务约束。

可依次检查参与者购票、组织者名单统计、管理员审核和现场检票。旧 mock 详情链接的固定 UUID 不对应真实库；请从“发现活动”或工作台重新进入。支付与退款仍由本地模拟器处理，没有外部支付平台或真实资金扣款。

## 导入方式与后台任务

已执行一次导入并验证重复执行返回 `ALREADY_PRESENT`。平时启动不需要重导。若在符合表结构的新本机开发库显式初始化，先构建，再运行：

```powershell
cd D:\2026Fall-SE\phase3\code\backend
& 'D:\javajdk17\bin\java.exe' -jar target/ticketing-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local-connected --server.port=18080 --app.jobs.enabled=false --app.demo.seed.enabled=true --app.demo.seed.exit-after=true
```

导入器只允许 `app.environment=local` 且当前数据库名为 `campus_ticketing_dev`。使用稳定 UUID 和导入标记，重复导入不覆盖账号、日期、密码或操作记录；发现用户名/手机号/类别冲突则拒绝，事务回滚。没有执行建表、迁移或清库，DDL 仍只在文档中。

当前后台使用显式参数 `--app.jobs.enabled=true`，每 5 秒扫描到期工作，负责超时订单、候补、结束结算、终止结算及保留期清理等。普通配置文件仍保留关闭默认值，重新启动请带上上方参数。用户已授权在此开发库执行这些自动状态变更；无需再次批准。不执行 Redis FLUSH，不把 Redis 当订单权威来源。

本轮同时补齐从权威订单恢复缺失到期任务：对WAITLISTED/OFFERED/UNPAID计算报名截止与各自确认/支付期限，恢复JOB-01并保持去重。实际重启后工坊过期订单已自动CANCELLED，MySQL只读核对超时UNPAID数量为0；任务记录显示JOB-01及候补/清理/治理扫描成功。新增回归验证缺失触发器恢复及再次扫描不重复通知。

本机连接凭据与 HMAC 密钥只在被 Git 忽略的 `application-local-private.yml` 中；本说明不包含 MySQL 密码。

## 已验证与仍需验收

本轮后端 **42 项测试通过**，Maven package 成功；前端类型检查/构建与既有 **6 项 mock 回归**通过。真实 HTTP 通过 Vite 的 `/api` 代理完成以下 **9 组检查**：

1. 健康检查 MySQL/Redis 均 UP。
2. 真实公开数据查询，5 个公开场次。
3. 登录、报名、同请求键重放返回同订单。
4. 模拟支付、重放不重复出票、下载真实文本票。
5. 两阶段退款及重放，票失效，一笔支付与一笔退款历史。
6. 组织者活动列表和结束活动统计快照。
7. 现场检票与重复签到返回原签到时间。
8. 管理员初始密码强制改密拦截。
9. 退出后私有接口返回 401。

连续写请求的 CSRF 问题已修复：请求中的 MySQL 会话恢复不会被当作每次新登录；仍保留 Cookie/请求头 CSRF 校验，登录/退出后重新取令牌，未引入 HttpSession 保存业务身份。相关机制见 [Spring Security 会话管理说明](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)。新增回归覆盖同一 CSRF 连续两次已认证 POST。

脚本：`code/backend/requests/http-demo-smoke.py`，在后端目录执行 `python requests/http-demo-smoke.py`。脚本会真实创建并退款测试订单、对现场票签到、登录演示角色，并因单会话规则撤销同账号原会话。请避免与人工操作同一联调账号同时执行。结果保存在被忽略的 `.tools/http-smoke-results.json`，不记录 Cookie/token。管理员改过密码或现场窗口关闭后不能原样重跑，需要按真实业务准备新的夹具。

这些结果覆盖本次垂直流程，不代表 89 个接口和 REQ-001～053 全分支已验收。并发抢票、跨进程恢复、全部保留期与处罚边界、数据库违规写入、性能和文件全流程仍按原验收清单继续验证。开发任务已启用也不等于 5 分钟故障恢复目标已通过。

[前端说明](../code/frontend/README.md) · [后端说明](../code/backend/README.md) · [接口约定](api.md) · [结构与连接核验](local-connection.md)
