SET NAMES utf8mb4;

-- 接口文档主信息
create table if not exists feiapi.`interface_doc`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `interface_info_id` bigint not null comment '接口信息 ID',
    `doc_version` varchar(64) default 'v1' not null comment '文档版本号',
    `request_content_type` varchar(128) default 'application/json' not null comment '请求内容类型',
    `response_content_type` varchar(128) default 'application/json' not null comment '响应内容类型',
    `auth_description` varchar(512) null comment '鉴权说明',
    `success_example` text null comment '成功响应 JSON 示例',
    `fail_example` text null comment '失败响应 JSON 示例',
    `remark` varchar(512) null comment '文档备注',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` bigint default 0 not null comment '逻辑删除标识 0-未删除 其他值-已删除记录 ID',
    unique key `uk_interface_doc_info_delete` (`interface_info_id`, `is_delete`)
) comment '接口文档主信息';

-- 接口文档参数
create table if not exists feiapi.`interface_doc_param`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `interface_info_id` bigint not null comment '接口信息 ID',
    `param_scene` varchar(32) not null comment '参数场景 QUERY/BODY/RESPONSE',
    `parent_id` bigint null comment '父级参数 ID',
    `name` varchar(128) not null comment '参数名称',
    `type` varchar(64) not null comment '参数类型',
    `required` tinyint default 0 not null comment '是否必填 0-否 1-是',
    `nullable` tinyint default 0 not null comment '是否允许为空 0-否 1-是',
    `default_value` varchar(512) null comment '默认值',
    `example_value` varchar(1024) null comment '示例值',
    `description` varchar(512) null comment '参数说明',
    `validation_rule` varchar(512) null comment '校验规则展示说明',
    `sort_order` int default 0 not null comment '排序值',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` bigint default 0 not null comment '逻辑删除标识 0-未删除 其他值-已删除记录 ID',
    key `idx_interface_doc_param_info_scene` (`interface_info_id`, `param_scene`, `is_delete`, `sort_order`)
) comment '接口文档参数';

-- 阶段 2 不再开放自定义业务 Header，统一逻辑删除阶段 1 遗留记录
update feiapi.`interface_doc_param`
set `is_delete` = `id`
where `param_scene` = 'HEADER'
  and `is_delete` = 0;

-- 接口文档错误码
create table if not exists feiapi.`interface_doc_error_code`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `interface_info_id` bigint not null comment '接口信息 ID',
    `error_code` varchar(64) not null comment '错误码',
    `error_message` varchar(256) not null comment '错误信息',
    `description` varchar(512) null comment '错误说明',
    `solution` varchar(512) null comment '解决建议',
    `sort_order` int default 0 not null comment '排序值',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` bigint default 0 not null comment '逻辑删除标识 0-未删除 其他值-已删除记录 ID',
    unique key `uk_interface_doc_error_code` (`interface_info_id`, `error_code`, `is_delete`),
    key `idx_interface_doc_error_info` (`interface_info_id`, `is_delete`, `sort_order`)
) comment '接口文档错误码';
