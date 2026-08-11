SET NAMES utf8mb4;

-- 接口变更审计日志
create table if not exists feiapi.`interface_change_log`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `interface_info_id` bigint not null comment '接口 ID',
    `interface_name` varchar(50) not null comment '接口名称快照',
    `change_type` varchar(32) not null comment '变更类型 CREATED、UPDATED、ONLINE、OFFLINE',
    `event_time` datetime default CURRENT_TIMESTAMP not null comment '事件发生时间',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '记录创建时间',
    key `idx_interface_change_log_interface_time` (`interface_info_id`, `event_time`),
    key `idx_interface_change_log_event_time` (`event_time`)
) comment '接口变更审计日志';
