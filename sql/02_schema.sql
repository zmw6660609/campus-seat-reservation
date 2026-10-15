-- ============================================================================
-- 座位预约平台 · 表结构
--
-- 数据库：seat_reservation（先执行 01_init.sql 建库）
-- 执行：mysql -uroot -p seat_reservation < 02_schema.sql
--
-- 层级设计：自习室（study_room）→ 座位（seat），两层。
-- 预约的粒度是「座位 × 日期 × 时段」，而不是座位本身 —— 这是整套设计的出发点。
--
-- 【相比早期版本的 5 处修正】
--   1. reservation 由「自由起止时间」改为「固定槽位」—— 恢复防超卖第一层
--   2. 新增 time_slot 时段表
--   3. seat.status 去掉「占用」语义（占用是"座位×时段"的属性，不是座位的属性）
--   4. seat 唯一索引纳入 deleted（否则删过的座位编号被永久占死）
--   5. user → sys_user；主键去掉 AUTO_INCREMENT（与 MyBatis-Plus 的 assign_id 对齐）
-- ============================================================================

-- 这一行不能删，也不能往下挪。
-- Windows 下的 mysql 命令行客户端默认跟随系统代码页（中文系统是 GBK），
-- 而本文件是 UTF-8 编码。不声明的话 Server 会把 UTF-8 的中文字节按 GBK 解读，
-- 「上午」就被存成了「涓婂崍」这种乱码——不报错，但数据已经坏了。
SET NAMES utf8mb4;

USE seat_reservation;

-- 兼容清理：早期版本把用户表命名为 `user`，而 `user` 也是 MySQL 系统库的名字，
-- 留着既容易混淆也容易误操作。这一行专门清掉那份历史遗留，首次执行无任何影响。
DROP TABLE IF EXISTS `user`;

DROP TABLE IF EXISTS `sys_role_permission`;
DROP TABLE IF EXISTS `sys_permission`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `time_slot`;
DROP TABLE IF EXISTS `seat`;
DROP TABLE IF EXISTS `study_room`;
DROP TABLE IF EXISTS `sys_user`;

-- ============================================================================
-- 1. 用户表
-- ============================================================================
CREATE TABLE `sys_user` (
    `id`           BIGINT UNSIGNED NOT NULL               COMMENT '主键，雪花ID（由 MyBatis-Plus 生成，表不加 AUTO_INCREMENT）',
    `username`     VARCHAR(50)     NOT NULL               COMMENT '登录账号（学号/工号）',
    `password`     VARCHAR(100)    NOT NULL               COMMENT 'BCrypt 密文。长度必须够：BCrypt 固定输出 60 字符',
    `name`         VARCHAR(30)     NOT NULL DEFAULT ''    COMMENT '真实姓名',
    `phone`        VARCHAR(20)     NOT NULL DEFAULT ''    COMMENT '手机号',
    `role`         TINYINT         NOT NULL DEFAULT 0     COMMENT '角色编码，对应 sys_role.code：0=学生 1=管理员。注册接口只会写 0，提权靠 SQL',
    `credit_score` INT             NOT NULL DEFAULT 100   COMMENT '信用分，初始 100，违约扣分（M3 用）',
    `status`       TINYINT         NOT NULL DEFAULT 1     COMMENT '状态：1=正常 0=禁用',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `version`      INT             NOT NULL DEFAULT 0    COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- 为什么 sys_user 没有 deleted 列？
--   "删除用户"在业务上就是"禁用用户"。用 status 表达，
--   唯一索引就能干净地写成 uk_username(username)。
--   如果硬上逻辑删除，就得写 uk(username, deleted)，代价是"同一学号只能被删一次"。

-- ============================================================================
-- 2. 自习室表
-- ============================================================================
CREATE TABLE `study_room` (
    `id`          BIGINT UNSIGNED NOT NULL                COMMENT '主键，雪花ID（表不加 AUTO_INCREMENT）',
    `name`        VARCHAR(100)    NOT NULL                COMMENT '自习室名称',
    `location`    VARCHAR(200)    NOT NULL DEFAULT ''     COMMENT '位置描述',
    `capacity`    INT             NOT NULL DEFAULT 0      COMMENT '规划容纳上限（只用于"新增座位时校验不超过容量"，不要拿它当实际座位数）',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=关闭',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=未删 1=已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_deleted` (`name`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='自习室';

-- 原字段名 total_seat 改名 capacity 的理由：
--   "总座位数"的真相在 seat 表里。在这里再存一份，加了座位忘了改就会不一致。
--   叫 total_seat 会让人以为它是权威值；叫 capacity 才表达的是"上限"。
--   诚实的数据命名，能省掉未来无数次"这两个数为什么不一样"的排查。

-- ★ status 为什么必须有？★
--   自习室会被"临时关闭"（装修、考试征用）。没有 status 就无法区分
--   "这个自习室不存在"和"这个自习室暂停开放"。
--   注意：这里的 status 是自习室自己的开关；某一天某个时段有没有空位，
--   由 reservation 表推导，绝对不要混进来。

-- ============================================================================
-- 3. 座位表
-- ============================================================================
CREATE TABLE `seat` (
    `id`          BIGINT UNSIGNED NOT NULL,
    `room_id`     BIGINT UNSIGNED NOT NULL                COMMENT '所属自习室（与 reservation.room_id 同名，保持一致）',
    `seat_no`     VARCHAR(20)     NOT NULL                COMMENT '座位编号，如 A-01',
    `row_num`     INT             NOT NULL DEFAULT 0      COMMENT '第几排（前端画座位图用）',
    `col_num`     INT             NOT NULL DEFAULT 0      COMMENT '第几列',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '物理状态：1=可用 0=停用/维修。（不含"占用"！见下方说明）',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_seat` (`room_id`, `seat_no`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='座位';

-- 没有单独建 KEY idx_room(room_id)：uk_room_seat 的第一列就是 room_id，
-- 按最左前缀原则，只查 room_id 也能用上这个唯一索引，再建一个是白占空间。
--
-- uk_room_seat 保证同一间自习室里座位编号不重复。
-- 批量生成座位时重复调用会被它拦下——和后面预约防超卖是同一个思路。
--   带上 deleted 的理由与 study_room 相同：逻辑删除后那一行还在，
--   不带 deleted 的话，"删掉 A-01 就再也建不了 A-01"。

-- ★★ status 里为什么不能有"占用"？★★
--   "占用"不是座位的属性，是"座位 × 时段"的属性。
--   同一个座位，上午被张三占了，下午还是空的。
--   如果把"占用"写进座位表：
--     · 下午查空座 → 座位显示"占用"，但下午根本没人约 → 查询结果直接错
--     · 第二天谁来把这个字段改回"空闲"？没人能改对。
--
--   座位表只回答"这个座位的物理状态是什么"；
--   预约表回答"这个座位在这个时段有没有人"。两件事必须分开。

-- ★★ 唯一索引为什么要把 deleted 算进去？★★
--   逻辑删除后那一行还在表里，uk(room_id, seat_no) 会一直占着这个编号，
--   导致"删了 A-03 就再也建不了 A-03"。带上 deleted 后：
--   未删除的行 deleted=0（互相唯一），已删除的行 deleted=1。
--   代价：同一编号只能被逻辑删除一次——对本项目够用。

-- ============================================================================
-- 4. 时段表
-- ============================================================================
CREATE TABLE `time_slot` (
    `id`          BIGINT UNSIGNED  NOT NULL,
    `slot_no`     TINYINT UNSIGNED NOT NULL               COMMENT '槽位序号 1/2/3，唯一',
    `name`        VARCHAR(32)      NOT NULL               COMMENT '时段名，如"上午"',
    `start_time`  TIME             NOT NULL               COMMENT '开始时间，如 08:00:00',
    `end_time`    TIME             NOT NULL               COMMENT '结束时间，如 12:00:00',
    `status`      TINYINT          NOT NULL DEFAULT 1     COMMENT '1=启用 0=停用',
    `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slot_no` (`slot_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='时段定义';

-- 时段用 TIME 而不是 DATETIME：它是"每天重复的规则"（上午永远是 8:00-12:00），不带日期。
-- 具体哪一天，存在 reservation.reserve_date 里。

INSERT INTO `time_slot` (`id`, `slot_no`, `name`, `start_time`, `end_time`, `status`) VALUES
    (1, 1, '上午', '08:00:00', '12:00:00', 1),
    (2, 2, '下午', '14:00:00', '18:00:00', 1),
    (3, 3, '晚上', '18:00:00', '22:00:00', 1);

-- ============================================================================
-- 5. 预约单表  ★★★ 核心表 ★★★
-- ============================================================================
CREATE TABLE `reservation` (
    `id`           BIGINT UNSIGNED NOT NULL,
    `user_id`      BIGINT UNSIGNED NOT NULL               COMMENT '预约人',
    `seat_id`      BIGINT UNSIGNED NOT NULL               COMMENT '座位',
    `room_id`      BIGINT UNSIGNED NOT NULL               COMMENT '冗余：所属自习室（统计/排行免 join）',
    `reserve_date` DATE            NOT NULL               COMMENT '预约日期（不含时段）',
    `slot_id`      BIGINT UNSIGNED NOT NULL               COMMENT '时段 ID',
    `status`       TINYINT         NOT NULL DEFAULT 1     COMMENT '1=已预约 2=已签到 3=已完成 4=已取消 5=违规未签到',
    `occupy_flag`  TINYINT UNSIGNED NULL DEFAULT 1        COMMENT '占用标记：1=占用中；NULL=已释放。★唯一索引对 NULL 不生效★',
    `checkin_time` DATETIME        NULL                   COMMENT '实际签到时间',
    `cancel_time`  DATETIME        NULL                   COMMENT '取消时间',
    `deadline`     DATETIME        NOT NULL               COMMENT '签到截止时间 = 时段开始 + 宽限期；超时由 M3 的延时队列自动释放',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    -- ★★★ 防超卖的第一道、也是最可靠的一道防线 ★★★
    UNIQUE KEY `uk_seat_date_slot` (`seat_id`, `reserve_date`, `slot_id`, `occupy_flag`),
    -- 每人每时段最多1个座：数据库兜底，应用层漏校验也拦得住
    UNIQUE KEY `uk_user_date_slot` (`user_id`, `reserve_date`, `slot_id`, `occupy_flag`),
    KEY `idx_user_date` (`user_id`, `reserve_date`),
    KEY `idx_room_date_slot` (`room_id`, `reserve_date`, `slot_id`),
    KEY `idx_status_deadline` (`status`, `deadline`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='预约单';

-- ============================================================================
-- 5. 权限（RBAC）
-- ============================================================================
-- 三张表解决一个问题：**登录 ≠ 有权**。
-- 在 M4-1 之前，任何注册用户都能调 POST/PUT/DELETE /study-room 改自习室 ——
-- LoginInterceptor 只校验 token 有效性，sys_user.role 建表起就没被任何一处读过。
--
-- ★ 为什么不建「标准 RBAC 五表」里的 sys_user_role？
--   1) 本项目一个用户只有一个角色（学生 **或** 管理员），多对多是「为不需要的灵活性付复杂度」；
--   2) sys_user.role 从 M1 就在，它已经是「用户角色」的唯一真相源。
--      再加一张 sys_user_role 就会出现**两个真相源** —— 和 study_room.capacity
--      不叫 total_seat 是同一个道理（诚实的数据命名，能省掉未来无数次"这两个数为什么不一样"）。
--
-- ★ 为什么 sys_role_permission 必须建？
--   它才是权限体系的价值所在。以后加一个「助教」角色（能看统计报表、不能改座位），
--   只要往这张表里插两行，**不用改代码、不用发版**。
--   直接写 if (role == 1) 的话，加角色就得改代码 —— 那等于没做权限体系。

CREATE TABLE `sys_role` (
    `code`        TINYINT      NOT NULL                COMMENT '角色编码，与 sys_user.role 对应',
    `name`        VARCHAR(20)  NOT NULL                COMMENT '角色名',
    `remark`      VARCHAR(100) NOT NULL DEFAULT ''     COMMENT '说明',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色字典';

-- 为什么用 TINYINT code 做主键，而不是雪花 id？
--   角色是「极少量 + 稳定」的字典数据（就两个），可读性比全局唯一更有用：
--   sys_user.role = 1 一眼就知道是管理员，不用再回表查一次。

CREATE TABLE `sys_permission` (
    `code`        VARCHAR(50)  NOT NULL                COMMENT '权限点编码，如 room:write',
    `name`        VARCHAR(30)  NOT NULL                COMMENT '权限名',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='权限点字典';

CREATE TABLE `sys_role_permission` (
    `role_code`       TINYINT     NOT NULL             COMMENT '对应 sys_role.code',
    `permission_code` VARCHAR(50) NOT NULL             COMMENT '对应 sys_permission.code',
    PRIMARY KEY (`role_code`, `permission_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色 -> 权限点';

-- 联合主键天然防重，不需要再建唯一索引。
-- 没上外键：和全项目保持一致（约束写在 Service 层 + 表注释里），
-- 换来的好处是插种子数据、清理数据时不用考虑外键顺序。

INSERT INTO `sys_role` (`code`, `name`, `remark`) VALUES
    (0, 'STUDENT', '学生：只能预约与查看'),
    (1, 'ADMIN',   '管理员：可管理自习室、座位与报表');

INSERT INTO `sys_permission` (`code`, `name`) VALUES
    ('room:write',        '自习室增删改'),
    ('seat:write',        '座位增删改与批量生成'),
    ('reservation:read',  '查看全部预约'),
    ('reservation:write', '人工处理预约（标记违约 / 释放座位）'),
    ('stats:read',        '查看统计报表');

INSERT INTO `sys_role_permission` (`role_code`, `permission_code`) VALUES
    (1, 'room:write'),
    (1, 'seat:write'),
    (1, 'reservation:read'),
    (1, 'reservation:write'),
    (1, 'stats:read');

-- 学生角色（code=0）的权限集合是**空的**：学生的接口都是「登录即可」，本来就没有需要挡的操作。
-- 这不是偷懒 —— 权限点是给「以后要细分」留的插槽（比如只让助教看报表）。
--
-- 管理员账号怎么来：注册接口永远写 role=0（不能让人自己注册成管理员），提权靠一条 SQL：
--   UPDATE sys_user SET role = 1 WHERE username = 'admin';


-- ============================================================================
-- 【本表最重要的三个设计】
--
-- Q1: 为什么不用 start_time + end_time？
--
--   因为那样唯一索引就废了：9:00-11:00 和 10:00-12:00 明显冲突，
--   但两列的「值」完全不同，数据库拦不住如何"重叠"。
--   改成 日期 + 固定槽位 之后，"同一座位同一时段"就变成了一组完全相同的值，
--   唯一索引能精确拦住。
--
-- Q2: occupy_flag 为什么是 1 或 NULL，而不是 1 和 0？
--
--   MySQL 唯一索引有一个特殊规则：**允许多行同时为 NULL**（NULL 不参与唯一性比较）。
--     · 占用中（status = 1 已预约 / 2 已签到）→ occupy_flag = 1
--       同一座位 + 同一天 + 同一时段只可能存在一条，并发抢座只会成功一单
--     · 已释放（3 已完成 / 4 已取消 / 5 违规）→ occupy_flag = NULL
--       可以存在任意多条，座位立刻可以被别人重新预约
--
--   如果写成 1 和 0 两个值，唯一索引会变成"同一座位同一时段只能有一条取消记录"，
--   第二次取消就写不进去了。
--
--   谁来维护它？Service 层：新建预约置 1，状态变为取消/完成/超时时置 NULL。
--   进阶做法是用虚拟生成列让数据库自动派生（留到 M3 讲一致性时对比）：
--     occupy_flag TINYINT GENERATED ALWAYS AS (IF(status IN (1,2), 1, NULL)) VIRTUAL
--
-- Q3: 为什么没有 deleted 列？
--
--   预约单永远不会被"删除"，只会在状态之间流转。
--   **不是每张表都要带 deleted —— 那取决于业务语义。**
-- ============================================================================
