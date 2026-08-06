SET NAMES utf8mb4;

-- 接口文档主信息
create table if not exists feiapi.`interface_doc`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `interface_info_id` bigint not null comment '接口信息 ID',
    `doc_status` varchar(16) default 'DRAFT' not null comment '文档状态 DRAFT-草稿 READY-已完成',
    `doc_version` varchar(64) default 'v1' not null comment '文档版本号',
    `request_content_type` varchar(128) default 'application/json' not null comment '请求内容类型',
    `response_content_type` varchar(128) default 'application/json' not null comment '响应内容类型',
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

-- 初始化测试接口草稿文档主信息
insert into feiapi.interface_doc (`interface_info_id`, `doc_status`, `doc_version`, `request_content_type`, `response_content_type`)
select interface_info.id,
       'DRAFT',
       'v1',
       'application/json',
       'application/json'
from feiapi.interface_info interface_info
where interface_info.`path` = '/api/name/user'
  and interface_info.`method` = 'POST'
  and interface_info.`is_delete` = 0
  and not exists (
      select 1
      from feiapi.interface_doc interface_doc
      where interface_doc.`interface_info_id` = interface_info.`id`
        and interface_doc.`is_delete` = 0
  );

-- 初始化测试接口结构化请求参数
insert into feiapi.interface_doc_param (`interface_info_id`, `param_scene`, `parent_id`, `name`, `type`, `required`,
                                        `nullable`, `default_value`, `example_value`, `description`, `validation_rule`,
                                        `sort_order`)
select interface_info.id,
       'BODY',
       null,
       'username',
       'string',
       1,
       0,
       '',
       '',
       '由接口运行时参数模板自动生成',
       '',
       1
from feiapi.interface_info interface_info
where interface_info.`path` = '/api/name/user'
  and interface_info.`method` = 'POST'
  and interface_info.`is_delete` = 0
  and not exists (
      select 1
      from feiapi.interface_doc_param interface_doc_param
      where interface_doc_param.`interface_info_id` = interface_info.`id`
        and interface_doc_param.`param_scene` = 'BODY'
        and interface_doc_param.`name` = 'username'
        and interface_doc_param.`is_delete` = 0
  );
