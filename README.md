# seat-reservation · 高校自习室座位预约平台

> 一个 Java 后端项目。全项目的核心命题只有一句话：
> **同一座位、同一天、同一时段，绝不能被两个人同时占走。**

---

## 为什么做这个

学校的自习室抢座很痛苦：到店发现没空位，或者座位被人长期占着不用。

现有的人工方式解决不了两件事：

1. **同一时段被重复占用** —— 两个人同时点同一个座位，系统必须保证只有一个成功
2. **约了不来** —— 座位被僵尸占用，必须能自动回收

这个项目就是围绕这两个问题设计的。

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.3.13 |
| 持久层 | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8 |
| 缓存 / 分布式 | Redis |
| 构建 | Maven（三模块） |
| 部署 | Docker Compose + Nginx |

**明确不引入**：Spring Cloud / Nacos、消息队列、Elasticsearch、分库分表、分布式事务框架。
理由：这些技术解决的是"多团队、多实例、超大规模"的问题，本项目一个都没有。
**把一件事做深，比把十个名词堆在简历上有用。**

## 工程结构

```
seat-reservation/               父工程，统一版本与模块聚合
├── sr-common/                  通用能力层（与业务无关）
│   └── com.sr.common
│       ├── result/             Result / ResultCode / PageResult
│       └── exception/          BizException
├── sr-pojo/                    领域模型层
│   └── com.sr.pojo
│       ├── entity/  dto/  vo/  enums/
└── sr-server/                  业务与接入层（唯一可启动模块）
    └── com.sr
        ├── controller/         接口层：只做参数校验 + 调用 Service
        ├── service/ impl/      业务逻辑与事务边界
        ├── mapper/             MyBatis-Plus Mapper
        ├── redis/              缓存 / 分布式锁 / 延时队列 / 限流
        ├── task/               定时任务
        ├── config/             配置
        ├── interceptor/        JWT 鉴权
        └── exception/          全局异常处理
```

依赖方向：`sr-server → sr-pojo → sr-common`，**单向，禁止反向**。

**模块划分依据是「复用边界」，不是「技术分层」。** 分层放在包这一层做（controller / service / mapper），模块层面只拆出真正的复用单元。

## 快速开始

```bash
# 1. 建库建表
mysql -uroot -p < sql/01_init.sql
mysql -uroot -p seat_reservation < sql/02_schema.sql

# 2. 配置本地连接
cp sr-server/src/main/resources/application-dev.yml.example \
   sr-server/src/main/resources/application-dev.yml
# 然后编辑它，填上你自己的 MySQL 密码

# 3. 编译
mvn clean compile

# 4. 启动
mvn -pl sr-server spring-boot:run

# 5. 自检
curl http://localhost:8080/health
```

## 开发进度

- [x] **M1 骨架跑通** —— 三模块 Maven 工程、统一返回体、全局异常、Redis 配置、环境分离
- [ ] **M2 核心闭环** —— 登录、基础数据管理、查空座、预约下单、取消、签到
- [ ] **M3 技术亮点** —— 分布式锁、Redis 延时队列超时释放、缓存与一致性、限流、排行榜
- [ ] **M4 完整与上线** —— RBAC 权限、统计报表、单元测试、Docker 部署上线

## 设计要点

### 防超卖第一层：数据库唯一索引

```sql
UNIQUE KEY uk_seat_date_slot (seat_id, reserve_date, slot_id, occupy_flag)
```

`occupy_flag` 只有两个取值：`1`（占用中）和 `NULL`（已释放）。

利用 **MySQL 唯一索引对 NULL 不生效** 的特性：只有"占用中"的记录才参与唯一性约束；
取消 / 完成 / 超时后置为 `NULL`，座位立刻可以被重新预约。

这不是终点——唯一索引管不了"查询信用分 → 检查冲突 → 扣减 → 写单"这一串多步操作之间的缝隙。
后面会依次引入乐观锁、Redis 分布式锁、Lua 原子操作来补位。**这是一条有递进的技术线。**
