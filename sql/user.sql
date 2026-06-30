SET NAMES utf8mb4;

-- 创建库
create database if not exists feiapi;

-- 切换库
use feiapi;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    user_name     varchar(256)                           null comment '用户昵称',
    user_account  varchar(256)                           not null comment '账号',
    user_avatar   varchar(1024)                          null comment '用户头像',
    gender       tinyint                                null comment '性别',
    user_role     varchar(256) default 'user'            not null comment '用户角色：user / admin',
    user_password varchar(512)                           not null comment '密码',
    access_key varchar(512) not null comment 'accessKey',
    secret_key varchar(512) not null comment 'secretKey',
    create_time   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint      default 0                 not null comment '是否删除',
    constraint uni_userAccount
        unique (user_account)
) comment '用户';

-- 初始化管理员账号
insert into user (user_name, user_account, user_role, user_password, access_key, secret_key)
select '管理员',
       'admin',
       'admin',
       '$2a$10$ZQ4BuFtcYf9963e/GSGrmu1BcFDKM0cZKg5G0UEaioME2jF8ORZbS',
       'test-admin-access-key',
       'test-admin-secret-key'
where not exists (
    select 1 from user where user_account = 'admin'
);
