SET NAMES utf8mb4;

-- 接口调用日志
create table if not exists feiapi.`interface_invoke_log`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `user_id` bigint not null comment '调用用户 ID',
    `interface_info_id` bigint not null comment '被调用接口 ID',
    `path` varchar(512) not null comment '接口请求路径',
    `method` varchar(16) not null comment 'HTTP 请求方法',
    `status_code` int null comment '下游响应状态码',
    `success` tinyint default 0 not null comment '是否调用成功 0-失败 1-成功',
    `response_time_ms` bigint default 0 not null comment '响应耗时，单位毫秒',
    `invoke_time` datetime default CURRENT_TIMESTAMP not null comment '调用发生时间',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `is_delete` tinyint default 0 not null comment '是否删除 0-未删除 1-已删除',
    key `idx_interface_invoke_log_invoke_time` (`invoke_time`),
    key `idx_interface_invoke_log_interface_time` (`interface_info_id`, `invoke_time`)
) comment '接口调用日志';
