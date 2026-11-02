# 本机 MySQL / Redis 接入记录

日期：2026-11-02。**这是接入阶段的历史核验记录。后续已导入演示数据、切换HTTP并按用户授权启用任务，当前账号、启动参数和业务联调结果以[HTTP演示说明](http-demo.md)为准。** 以下“未初始化/前端mock/任务关闭”描述的是首次接通时的状态；连接配置、版本及结构核验仍适用。

## 当前配置

| 服务 | 连接位置 | 实测版本 |
| --- | --- | --- |
| MySQL | localhost:3306 / campus_ticketing_dev | 8.0.44 |
| Redis | localhost:6379，无密码 | 5.0.14.1 |

MySQL凭据和随机生成的持久HMAC密钥保存在后端根目录`application-local-private.yml`；该文件已确认由Git忽略，不打包进JAR。不要将其复制进公共文档、示例配置或提交到Git。原规划的8.4/7.4是设计参考，本机验证以本表版本为准，不将连接成功视为所有版本兼容性和业务约束均已验收。

## 启动完整本地连接模式

在VS Code的PowerShell终端运行：

```powershell
cd D:\2026Fall-SE\phase3\code\backend
$env:JAVA_HOME='D:\javajdk17'
mvn.cmd '-Dspring-boot.run.profiles=local-connected' spring-boot:run
```

也可运行已构建的JAR：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/ticketing-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local-connected
```

默认端口8080，只监听127.0.0.1。打开`http://127.0.0.1:8080/api/system/health`，正常应为HTTP200、mysql=UP、redis=UP。

其他模式仍可使用：

- 不指定profile：保持离线模式，不加载个人配置，回归测试不依赖本机服务。
- `mysql-local`：只接MySQL，明确关闭Redis；健康503且Redis=NOT_CONNECTED，公开只读查询可以正常工作，认证入口因限流依赖缺失而拒绝执行。
- `local-connected`：接入本机MySQL和Redis。Flyway、SQL自动初始化、后台任务仍关闭，没有自动种子账号。

MySQL与Redis开关已经分离；不会仅因需要MySQL就偷偷连接默认Redis。

## 已完成的验证

- MySQL真实连接成功，现有33张表均为InnoDB、utf8mb4_0900_bin，主键均为id。
- 按原DDL核对347个字段（含类型、可空性、生成列标记）、规划的索引及69个外键，未发现差异；数据库实际有132个索引，包含主键与外键辅助索引。
- Java应用的MySQL单连接模式健康状态为mysql=UP、redis=NOT_CONNECTED；完整本机模式健康HTTP200，两个组件均UP。
- 公开类别和公开场次接口实际通过Java/JDBC查询MySQL，均返回HTTP200；不以mock替代。
- Redis基础限流Lua在独立`ticketing:test`命名空间验证计数1→2、正TTL且后续计数不延长窗口；测试key设置3秒TTL，并已定点删除，没有执行FLUSH/扫描/清空。
- 后端39项既有离线回归测试通过，JAR重新打包成功。测试临时使用18080端口，已停止测试进程；日常命令仍使用8080。

本轮数据库操作仅为版本/结构/公开数据读取和连接检查；没有执行CREATE/ALTER/DROP，没有创建用户、订单或演示数据。没有验证所有CHECK的违规写入拒绝、真实支付/退款事务、竞争锁、宕机恢复和性能指标。

## 前端与后续状态

**前端仍为mock模式。** 连接后端依赖不会自动改变前端`VITE_API_MODE`；当前网页购票仍写浏览器演示存储。

下一步打通真实业务，需要确认首个管理员的受控初始化方式（目前无默认管理员或口令）、建立真实角色账号和活动，然后切换前端HTTP并完成垂直流程联调。后台任务应在独立测试库验证持久执行后显式启用；本轮没有擅自启用任务清理或结算。

[后端运行说明](../code/backend/README.md) · [业务初版及剩余验收](backend-implementation.md) · [数据库设计](database-design.md) · [Redis约定](redis-design.md)
