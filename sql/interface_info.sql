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
    `is_delete` tinyint default 0 not null comment '是否删除 0-未删除 1-已删除',
    unique key `uk_interface_info_path_method_delete` (`path`(191), `method`, `is_delete`)
    ) comment '接口信息';

-- 初始化土味情话接口
insert into feiapi.interface_info (`name`, `sdk_method_name`, `description`, `url`, `path`, `target_host`, `request_params`, `request_header`, `response_header`, `status`, `method`, `quota_type`, `user_id`)
select '土味情话',
       'getLoveWords',
       '随机获取一条土味情话',
       'http://feiapi-interface:8123/api/love_words',
       '/api/love_words',
       'http://feiapi-interface:8123',
       '',
       '',
       '',
       1,
       'GET',
       'FREE_UNLIMITED',
       (select id from feiapi.user where user_account = 'admin' limit 1)
where not exists (
    select 1 from feiapi.interface_info where `path` = '/api/love_words' and `method` = 'GET' and `is_delete` = 0
);

-- 初始化用户名称查询接口
insert into feiapi.interface_info (`name`, `sdk_method_name`, `description`, `url`, `path`, `target_host`, `request_params`, `request_header`, `response_header`, `status`, `method`, `quota_type`, `user_id`)
select '测试接口',
       'getUsernameByPost',
       '根据用户对象获取用户名（测试接口）',
       'http://feiapi-interface:8123/api/name/user',
       '/api/name/user',
       'http://feiapi-interface:8123',
       '{\"username\":\"string\"}',
       'Content-Type: application/json',
       '',
       1,
       'POST',
       'FREE_UNLIMITED',
       (select id from feiapi.user where user_account = 'admin' limit 1)
where not exists (
    select 1 from feiapi.interface_info where `path` = '/api/name/user' and `method` = 'POST' and `is_delete` = 0
);

-- 初始化二维码生成接口
insert into feiapi.interface_info (`name`, `sdk_method_name`, `description`, `url`, `path`, `target_host`, `request_params`, `request_header`, `response_header`, `status`, `method`, `quota_type`, `user_id`)
select '二维码生成',
       'generateQrCode',
       '根据内容生成二维码图片，返回 Base64 编码和 Data URI，支持自定义尺寸',
       'http://feiapi-interface:8123/api/qrcode/generate',
       '/api/qrcode/generate',
       'http://feiapi-interface:8123',
       '{\"content\":\"string，1-1024字符且不超过1024个UTF-8字节\",\"width\":300,\"height\":300}',
       'Content-Type: application/json',
       '',
       1,
       'POST',
       'FREE_UNLIMITED',
       (select id from feiapi.user where user_account = 'admin' limit 1)
where not exists (
    select 1 from feiapi.interface_info where `path` = '/api/qrcode/generate' and `method` = 'POST' and `is_delete` = 0
);
