> 接入更新：本机Redis5.0.14.1已连接并通过基础Lua计数/TTL检查，详见[本机接入记录](local-connection.md)。以下保留设计和原离线验证边界，未代表已完成并发/故障验收。

# Redis 用途、分类与键约定

版本：v0.2 · 2026-11-02 · 当前已实现Redis窗口计数适配器、HMAC指纹、登录三维限流和429/Retry-After策略；认证用例已接限流调用。**未连接Redis、未写入key、未验证真实Lua/TTL**；默认依赖未连接，见[业务交付](backend-implementation.md)。

## 1. 分类

| 分类 | 内容 | 本轮状态 | 丢失/故障处理 |
| --- | --- | --- | --- |
| 必需技术保护 | 登录、注册、人工找回入口频率 | 已接认证Service，默认无连接 | 接入后 Redis 故障时受保护入口503；已登录业务不依赖这组计数 |
| 可选性能缓存 | 按内容SHA256定位的不可变海报元数据 | 延后，未实现、默认关闭 | miss查MySQL；故障绕过缓存；权限先查MySQL |
| 可选运维观测 | 非权威任务耗时/错误指标 | 延后，未实现 | 可丢失，不能替代 jobs/job_items/audit_logs |
| 禁止作为唯一事实 | 会话撤销、账号权限、库存、订单、幂等、退款资格、消息、处罚、每日发布/举报业务额度、结算进度与正式统计 | 全部归MySQL | Redis重启、淘汰或清空不能改变资格或制造第二次成功 |

本系统实时余票、实时计数和权限要求下一次查询反映已提交事实，初期不放短时TTL缓存。正式统计虽是快照，也先从MySQL读取并实时检查7天边界；不建立能绕过期限的缓存入口。CSRF使用Spring Cookie token repository；TICKET_SESSION只存MySQL tokenHash，不混入Redis登录缓存。

## 2. 命名维度

统一前缀 `ticketing:<env>:`，env为`local/test/staging/prod`等小写字母、数字、下划线或连字符（1～24字符）。不在key中拼密码、Cookie、原始用户名、手机号、完整IP、证据正文。实例与数据库编号即使相同，也必须用不同env隔离。

限流指纹为 `HMAC-SHA256(secret, version + action + dimension + canonicalValue)` 的64位小写十六进制；以明确长度或规范JSON编码各分量，避免拼接歧义。secret通过 `APP_RATE_HMAC_SECRET` 外部配置提供，不写进Git或日志；不用可穷举的裸手机号SHA256。client默认来自TCP远端地址；只有明确配置可信代理时才接受代理转发信息，不能信任任意X-Forwarded-For。

| key模板 | Redis类型/值字段 | TTL/窗口 | 初始阈值 | 用途 |
| --- | --- | --- | --- | --- |
| `ticketing:<env>:rate:login:client:<hmac>` | String，整数`count` | 固定窗口60秒 | 60次 | 限制单客户端轮换账号撞库 |
| `ticketing:<env>:rate:login:account:<hmac>` | String，整数`count` | 60秒 | 30次 | 限制同账号多客户端尝试 |
| `ticketing:<env>:rate:login:pair:<hmac>` | String，整数`count` | 60秒 | 10次 | 同客户端+规范账号组合 |
| `ticketing:<env>:rate:register:client:<hmac>` | String，整数`count` | 600秒 | 5次 | 注册技术请求频率 |
| `ticketing:<env>:rate:recovery:client:<hmac>` | String，整数`count` | 600秒 | 5次 | 人工找回技术请求频率 |
| `ticketing:<env>:cache:poster-meta:v1:<sha256>` | String，UTF-8 JSON：`fileId,sha256,contentType,byteSize` | 可选300秒 | 不限次数 | 未来不可变内容信息；不含路径、授权、可读期限 |
| `ticketing:<env>:metric:job:<jobType>:<utcMinute>` | 可选Hash：`completed,failed,durationMsSum` | 可选24小时 | 不作为业务判定 | 未来性能观测；丢失不影响任务恢复 |

阈值是可调整的技术默认值，不是原实验新增的业务处罚。所有适用维度都通过才继续认证；拒绝也计入窗口。多个key逐个原子计数即可，不宣称跨key/跨MySQL事务原子；中途故障返回503，已增加的短期技术计数可自然过期。固定窗口在边界存在突发，后续如实测需要再改滑动窗口并更新契约。

## 3. 原子计数和失效

准备好的 `RedisTransientDao.incrementWindow` 执行以下单key Lua；首次计数与TTL设置同脚本，旧异常无TTL key也补TTL。重复请求不会延长已有正常窗口。

```lua
local n = redis.call('INCR', KEYS[1])
if n == 1 or redis.call('PTTL', KEYS[1]) < 0 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return n
```

接入后：超过任一阈值返回429 TOO_MANY_REQUESTS，Retry-After取适用窗口剩余秒数向上取整；Redis故障503 SERVICE_UNAVAILABLE。countWindow适配器由同一Lua返回count和remainingMs；RateLimitService执行全部适用维度，并按剩余TTL计算Retry-After。代码已写，真实Redis行为未验收。[Redis官方INCR/限流说明](https://redis.io/docs/latest/commands/incr/)

登录成功不随意清全客户端计数；不执行KEYS、FLUSHDB或FLUSHALL；清测试key只按明确test前缀SCAN并逐个删除。可选不可变海报缓存按内容换新key，不缓存旧图授权；每次读取先校验活动/归属，再读取缓存或文件。MySQL提交后可选缓存失效失败不能把已成功支付/退款响应改成“失败”。

## 4. 后续连接验收

在独立test命名空间验证Lua并发计数、首次TTL、到期重置、无TTL修复、跨环境隔离及故障503；验证正常窗口不会被后续请求延期。再验证客户端维度可信来源、HMAC无原文、阈值与Retry-After。**本轮已做key/HMAC、限流维度及故障分支单元测试，未执行这些真实Redis验收。**
