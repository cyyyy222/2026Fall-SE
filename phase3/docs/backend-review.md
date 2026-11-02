# 后端准备阶段自审与变更记录

日期：2026-11-02 · 依据：phase1 version5、phase2 UML、project/api v0.3、现有桌面前端调用。这里只记录静态复核和框架检查，不能承诺业务100%无遗漏。

## 已修订的问题

| 发现 | 修订 |
| --- | --- |
| 旧计划把登录、密码变更、自动迁移与管理员初始化算作搭架必做，与本次审阅前不做核心业务冲突 | 当前阶段只有CSRF和诊断基础设施真实可用；88项业务501。默认没有数据库/Redis客户端，关闭Flyway及SQL初始化，无初始管理员 |
| 原表组和DAO命名仍有概念别名（sessions、inbox_messages、termination_items等） | 以database-design.md中33张表及对应Entity/Dao命名为准；任务用jobs/job_items，终止资格用termination_facts，消息用messages，场次用event_sessions |
| 缺少完整建表命令，无法检查条件唯一在MySQL的落地 | 提供完整DDL与字段字典；可空生成列保护有效登录标识、单有效订单、唯一成功、单未关闭退款依据；不执行SQL |
| 删除草稿场次与授权历史外键/历史审计可能冲突 | 草稿场次、票种逻辑删除，撤销并逻辑删除关联检票账号；不级联删除授权和签到事实 |
| 每日100发布额度可能被误实现为每组织者100 | publication_quotas以北京时间全站自然日唯一，事务条件更新；非组织者各自100 |
| 只留订单会丢失同一已有单的多个报名请求键 | registration_requests永久保存每个用户+requestId映射与原参数指纹 |
| 单一退款状态无法表达先受理后执行及宕机恢复 | refund_requests保存初始attemptId、原受理键、资格快照和操作；refund_attempts独立保存执行，原成功支付外键/金额核对由Service保证 |
| 以Redis SET NX或内存结果代替可靠幂等/任务会丢事实 | MySQL operation_records、jobs/job_items及各业务唯一键持久化；Redis只技术辅助 |
| 注销后处罚、第三次注销黑名单可能丢失 | phone_identities、behavior_facts、restrictions、restriction_facts独立长期保留，账号换ID仍关联身份 |
| 终止时只遍历订单会漏无订单的组织者/检票员通知 | job_items有独立RECIPIENT_NOTICE项目；termination_facts固化最初逐单资格，重试不重算生效时间 |
| PATCH普通nullable无法区分省略/清空 | PatchField三态、非空补丁约束及泛型值校验；草稿必现可空字段单独标注required，禁止用默认0表示缺失 |
| 可省略字段若用普通nullable会接受显式null；record上的Nulls.FAIL又会误拒绝省略 | 专用OmittedNonNullDeserializer区分缺失与显式null，已验证导出筛选；PATCH仍用三态字段 |
| Service若只拿body/actor，后续无法可靠取得请求幂等键 | RequestContext由服务器解析身份、UUID键和trace；不允许body指定操作者 |
| 全部Controller写死200会破坏首次创建/重放/异步202 | UseCaseResult封装成功HTTP状态和Cookie元数据，data单独序列化；DownloadResult封装文件，授权通过后才输出 |
| 错误只在Advice处理会漏Security错误并返回HTML | Filter链与MVC共用JSON信封；覆盖CSRF/匿名/权限、绑定、格式、上传超限及未知异常 |
| 框架通过不等于数据库约束和并发已验收 | 默认离线测试，与真实MySQL DDL、锁、事务、Redis Lua及性能验收分别记录；未提供假的集成测试结果 |

## 需求覆盖静态复核

继续使用[53条追踪表](requirements-traceability.md)作为需求基线，不增加第二套需求编号。按下列横切路径复核存储：

| 路径 | 本轮准备的落点 |
| --- | --- |
| 四角色、注销/找回/单会话/强改密 | users、phone_identities、login_sessions、recoveries、checker_assignments |
| 类别、草稿、审核、排期、100额度、海报 | categories、events、event_sessions、ticket_types、event_reviews、publication_quotas、stored_files |
| 报名/候补/占位/支付/退款/历史票 | orders、registration_requests、payment_attempts、refund_requests、refund_attempts、tickets |
| 首次签到、账号改绑后留历史、正常结算 | checkins、checker_assignments、statistics_snapshots、behavior_facts |
| 举报阈值、同事实去重、15天限制与事实消费 | reports、investigations、investigation_reports、behavior_facts、restrictions、restriction_facts |
| 消息、日志、可靠幂等、7/180天保留、终止与CSV恢复 | messages、audit_logs、operation_records、jobs、job_items、termination_facts、exports、stored_files |

## 仍待真实实现验证

DDL未在MySQL执行，外键/生成列/索引需在独立测试库验证；SQL不能只经文本检查就宣称可用。跨表一致性、库存公平性、锁顺序/死锁、退款T1/T2、期限和权限都属于后续Service核心。模糊搜索、33表索引及1/5分钟任务目标需真实EXPLAIN、压测和故障恢复，现阶段不承诺性能达标。

前端仍使用mock，浏览器会话不能直接成为Java身份。UUID、字段名、HTTP路径保持api.md；现有前端宽类型（例如User.mustChangePassword:boolean）尚需在真实联调时按最小用户投影收紧，不能因TS当前可编译就认为所有响应空值/投影均已联调。
