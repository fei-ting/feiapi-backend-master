SET NAMES utf8mb4;

-- 接口配额类型配置
create table if not exists feiapi.`interface_quota_config`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `quota_type` varchar(32) not null comment '配额类型',
    `initial_quota` int default 0 not null comment '初始发放额度',
    `description` varchar(256) null comment '配置说明',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` tinyint default 0 not null comment '是否删除 0-未删除 1-已删除',
    unique key `uk_interface_quota_config_type_delete` (`quota_type`, `is_delete`)
) comment '接口配额类型配置';

insert into feiapi.interface_quota_config (`quota_type`, `initial_quota`, `description`)
select 'FREE_UNLIMITED', 0, '免费无限接口'
where not exists (
    select 1 from feiapi.interface_quota_config where `quota_type` = 'FREE_UNLIMITED' and `is_delete` = 0
);

insert into feiapi.interface_quota_config (`quota_type`, `initial_quota`, `description`)
select 'BASIC_QUOTA', 100, '基础额度接口'
where not exists (
    select 1 from feiapi.interface_quota_config where `quota_type` = 'BASIC_QUOTA' and `is_delete` = 0
);

insert into feiapi.interface_quota_config (`quota_type`, `initial_quota`, `description`)
select 'ADVANCED_TRIAL', 3, '高级体验接口'
where not exists (
    select 1 from feiapi.interface_quota_config where `quota_type` = 'ADVANCED_TRIAL' and `is_delete` = 0
);
