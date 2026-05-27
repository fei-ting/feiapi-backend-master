SET NAMES utf8mb4;

-- 接口信息
create table if not exists feiapi.`interface_info`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `name` varchar(256) not null comment '名称',
    `description` varchar(512) null comment '描述',
    `url` varchar(512) not null comment '接口地址',
    `requestParams` text null comment '请求参数',
    `requestHeader` text null comment '请求头',
    `responseHeader` text null comment '响应头',
    `status` int default 0 not null comment '接口状态  0-关闭 1-开启',
    `method` varchar(256) not null comment '请求类型',
    `userId` bigint not null comment '创建人',
    `createTime` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `updateTime` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `isDelete` tinyint default 0 not null comment '是否删除 0-未删除 1-已删除'
    ) comment '接口信息';

-- 初始化土味情话接口
insert into feiapi.interface_info (`name`, `description`, `url`, `requestParams`, `requestHeader`, `responseHeader`, `status`, `method`, `userId`)
select 'getLoveWords',
       '随机获取一条土味情话',
       'http://feiapi-interface:8123/api/love_words',
       '',
       '',
       '',
       1,
       'GET',
       (select id from feiapi.user where userAccount = 'admin' limit 1)
where not exists (
    select 1 from feiapi.interface_info where `name` = 'getLoveWords' and `method` = 'GET'
);

-- 初始化用户名称查询接口
insert into feiapi.interface_info (`name`, `description`, `url`, `requestParams`, `requestHeader`, `responseHeader`, `status`, `method`, `userId`)
select 'getUsernameByPost',
       '根据用户对象获取用户名（测试接口）',
       'http://feiapi-interface:8123/api/name/user',
       '{\"username\":\"string\"}',
       'Content-Type: application/json',
       '',
       1,
       'POST',
       (select id from feiapi.user where userAccount = 'admin' limit 1)
where not exists (
    select 1 from feiapi.interface_info where `name` = 'getUsernameByPost' and `method` = 'POST'
);
