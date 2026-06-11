SET NAMES utf8mb4;

-- 切换库
use feiapi;

-- 用户角色变更审计日志表
create table if not exists user_role_change_log
(
    id             bigint auto_increment comment 'id' primary key,
    operator_id    bigint                                 not null comment '操作者 id',
    target_user_id bigint                                 not null comment '目标用户 id',
    old_role       varchar(256)                           not null comment '旧角色',
    new_role       varchar(256)                           null comment '新角色（删除用户时为空）',
    remark         varchar(512)                           null comment '备注',
    create_time    datetime     default CURRENT_TIMESTAMP not null comment '创建时间'
) comment '用户角色变更审计日志';

-- 添加索引
create index idx_target_user_id on user_role_change_log (target_user_id);
create index idx_operator_id on user_role_change_log (operator_id);
create index idx_create_time on user_role_change_log (create_time);
