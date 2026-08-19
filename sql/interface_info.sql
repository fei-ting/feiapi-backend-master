SET NAMES utf8mb4;

-- 接口信息
create table if not exists feiapi.`interface_info`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `name` varchar(50) not null comment '接口名称',
    `sdk_method_name` varchar(128) null comment 'SDK 方法名，用于发布验证和在线调用',
    `description` varchar(512) null comment '描述',
    `url` varchar(512) not null comment '接口展示地址，主要用于前端展示和兼容旧数据',
    `path` varchar(512) not null comment '接口路径，用于网关路由和接口唯一身份匹配',
    `target_host` varchar(512) not null comment '真实后端服务地址',
    `request_params` text null comment '请求参数',
    `request_header` text null comment '请求头文档，不参与网关鉴权和路由',
    `response_header` text null comment '响应头文档，不参与网关运行时逻辑',
    `status` int default 0 not null comment '接口状态 0-下线 1-上线 2-发布验证中',
    `method` varchar(16) not null comment '请求方法',
    `quota_type` varchar(32) default 'BASIC_QUOTA' not null comment '接口配额类型',
    `user_id` bigint not null comment '创建人',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` bigint default 0 not null comment '逻辑删除标识 0-未删除 其他值-已删除记录 ID',
    unique key `uk_interface_info_path_method_delete` (`path`(191), `method`, `is_delete`)
    ) comment '接口信息';
