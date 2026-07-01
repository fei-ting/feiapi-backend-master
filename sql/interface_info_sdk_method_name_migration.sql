SET NAMES utf8mb4;

-- 为接口信息补充 SDK 方法名字段，用于发布验证和在线调用。
alter table feiapi.interface_info
    add column if not exists `sdk_method_name` varchar(128) null comment 'SDK 方法名，用于发布验证和在线调用' after `name`;

-- 修复内置土味情话接口的 SDK 方法名，保留中文展示名称。
update feiapi.interface_info
set `sdk_method_name` = 'getLoveWords'
where `path` = '/api/love_words'
  and `method` = 'GET'
  and `is_delete` = 0;

-- 修复内置用户名称查询接口的 SDK 方法名，保留中文展示名称。
update feiapi.interface_info
set `sdk_method_name` = 'getUsernameByPost'
where `path` = '/api/name/user'
  and `method` = 'POST'
  and `is_delete` = 0;
