# campus-seat-reservation · 高校自习室座位预约平台

> 一个 Java 后端项目。全项目的核心命题只有一句话：
> **同一座位、同一天、同一时段，绝不能被两个人同时占走。**

---

## 为什么做这个

学校的自习室抢座很痛苦：到店发现没空位，或者座位被人长期占着不用。

现有的人工方式解决不了两件事：

1. **同一时段被重复占用** —— 两个人同时点同一个座位，系统必须保证只有一个成功
2. **约了不来** —— 座位被僵尸占用，必须能自动回收

这个项目就是围绕这两个问题设计的。

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | 项目锁定 17（`java.version=17`）。用 21 编译会报「无效的目标发行版」 |
| Maven | 3.9+ | 也可以直接用仓库自带的 `./mvnw`，**无需本机安装 Maven** |
| MySQL | 8.0+ | 需要 `utf8mb4` 字符集 |
| Redis | 6.0+ | M1 阶段仅做连接配置；从 M2 的登录态开始成为必需 |
| Git | 任意 | |

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
理由：这些技术解决的是"多团队、多实例、超大规模"的问题，这个项目一个都没有。
**把一件事做深，比堆十个名词有用。**

## 工程结构

```
campus-seat-reservation/           父工程：统一版本 + 模块聚合（packaging=pom）
├── sql/
│   ├── 01_init.sql                建库
│   ├── 02_schema.sql              建表（设计说明写在表定义旁边）
│   └── 03_verify_unique_index.sql 唯一索引防重复预约的验收脚本
├── sr-common/                     通用能力层（与业务无关，可被任意模块依赖）
│   └── com.sr.common
│       ├── result/                Result / ResultCode / PageResult
│       └── exception/             BizException
├── sr-pojo/                       领域模型层（只放数据类，不含逻辑）
│   └── com.sr.pojo
│       ├── entity/  dto/  vo/  enums/
└── sr-server/                     业务与接入层（唯一可启动模块）
    └── com.sr
        ├── controller/           接口层：只做参数校验 + 调用 Service
        ├── service/ impl/        业务逻辑与事务边界
        ├── mapper/               MyBatis-Plus Mapper
        ├── redis/                缓存 / 分布式锁 / 延时队列 / 限流
        ├── task/                 定时任务
        ├── config/               配置
        ├── interceptor/          JWT 鉴权
        └── exception/            全局异常处理
```

依赖方向：`sr-server → sr-pojo → sr-common`，**单向，禁止反向**。

**模块划分依据是「复用边界」，不是「技术分层」。** 分层放在包这一层做（controller / service / mapper），模块层面只拆出真正的复用单元。

## 数据库表一览

| 表 | 职责 | 关键设计 |
|---|---|---|
| `sys_user` | 用户（学生 / 管理员） | `uk_username`。「删除用户」用 `status` 表达，不用逻辑删除 |
| `study_room` | 自习室 | `uk_room_name(room_name, deleted)`。`capacity` 只是规划上限，权威座位数在 `seat` 表 |
| `seat` | 座位 | `uk_room_seat(room_id, seat_no, deleted)`。**`status` 里不含"占用"语义** |
| `time_slot` | 时段定义（上午 / 下午 / 晚上） | 仅 3 行，`TIME` 类型。它的存在是唯一索引能生效的前提 |
| `reservation` | 预约单 | `uk_seat_date_slot(seat_id, reserve_date, slot_id, occupy_flag)` ← **防超卖第一层** |

## 快速开始

```bash
# 0. 拉代码
git clone https://github.com/zmw6660609/campus-seat-reservation.git
cd campus-seat-reservation

# 1. 建库建表（需要本机 MySQL 8 已启动）
mysql -uroot -p < sql/01_init.sql
mysql -uroot -p seat_reservation < sql/02_schema.sql

# 2. 本地配置（application-dev.yml 不进版本库，从示例复制一份）
cp sr-server/src/main/resources/application-dev.yml.example \
   sr-server/src/main/resources/application-dev.yml
# 然后编辑它，填上你自己的 MySQL 密码与 Redis 地址

# 3. 编译（两种方式任选其一）
./mvnw clean compile      # 用仓库自带的 Maven Wrapper，无需本机装 Maven
mvn clean compile         # 或使用本机 Maven 3.9+
```

Windows 下把 `./mvnw` 换成 `mvnw.cmd`。

```bash
# 4. 启动
./mvnw -pl sr-server spring-boot:run

# 5. 自检
curl http://localhost:8080/health
```

### 排错：`JAVA_HOME is not defined correctly`

`mvnw` 自己会下载 Maven，但**不会自己找 JDK**。如果机器上装了 JDK 却没配 `JAVA_HOME`
（IDEA 里能跑、命令行里报这个错，就是这个原因），先把它指到 JDK 17：

```bash
# Git Bash / macOS / Linux
export JAVA_HOME=/path/to/jdk-17

# Windows CMD
set JAVA_HOME=D:\path\to\jdk-17
```

必须是 **JDK 17**。项目三处锁定了 17（`java.version`、IDEA 的 Project SDK、Language level），
用 21 编译会报「无效的目标发行版: 17」。

## 开发进度

- [x] **M1 骨架跑通** —— 三模块 Maven 工程、统一返回体、全局异常、Redis 配置、环境分离
- [ ] **M2 核心闭环** —— 表设计 / 登录注册 / 自习室与座位管理 / 查空座 / 预约下单 / 取消 / 签到
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
如果写成 `1` 和 `0` 两个值，唯一索引就变成"同一座位同一时段只能有一条取消记录"，第二次取消会写不进去。

配套的另一个前提是**时段必须固定**：预约单存的是 `reserve_date` + `slot_id`，而不是自由起止时间。
因为 `9:00-11:00` 和 `10:00-12:00` 明明冲突，两列的值却完全不同，唯一索引拦不住"重叠"。
详见 [`sql/02_schema.sql`](sql/02_schema.sql) 的注释。

这不是终点——唯一索引管不了"查询信用分 → 检查冲突 → 扣减 → 写单"这一串多步操作之间的缝隙。
后面会依次引入乐观锁、Redis 分布式锁、Lua 原子操作来补位。**这是一条有递进的技术线。**

## License

[MIT](LICENSE)
