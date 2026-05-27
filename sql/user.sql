SET NAMES utf8mb4;

-- 创建库
create database if not exists feiapi;

-- 切换库
use feiapi;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userName     varchar(256)                           null comment '用户昵称',
    userAccount  varchar(256)                           not null comment '账号',
    userAvatar   varchar(1024)                          null comment '用户头像',
    gender       tinyint                                null comment '性别',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user / admin',
    userPassword varchar(512)                           not null comment '密码',
    `accessKey` varchar(512) not null comment 'accessKey',
    `secretKey` varchar(512) not null comment 'secretKey',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    constraint uni_userAccount
        unique (userAccount)
) comment '用户';

-- 初始化管理员账号
insert into user (userName, userAccount, userRole, userPassword, accessKey, secretKey)
select '管理员',
       'admin',
       'admin',
       '$2a$10$vB0k5F8jA3ny0mFkEQMroezxtYpDT4q0/EGfra9VCEd9uuh.5TWjO',
       'test-admin-access-key',
       'test-admin-secret-key'
where not exists (
    select 1 from user where userAccount = 'admin'
);
