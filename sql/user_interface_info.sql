-- 用户调用接口关系表
create table if not exists feiapi.`user_interface_info`
(
    `id` bigint not null auto_increment comment '主键' primary key,
    `user_id` bigint not null comment '调用用户id',
    `interface_info_id` bigint not null comment '接口id',
    `total_num` int default 0 not null comment '总调用次数',
    `left_num` int default 0 not null comment '剩余调用次数',
    `status` int default 0 not null comment '0-正常, 1-禁用',
    `create_time` datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    `update_time` datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    `is_delete` tinyint default 0 not null comment '是否删除 0-未删, 1-已删',
    unique key `uk_user_interface_info_user_interface` (`user_id`, `interface_info_id`)
) comment '用户调用接口关系';
