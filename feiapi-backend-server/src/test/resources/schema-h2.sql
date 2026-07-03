-- H2 测试数据库建表脚本（兼容 MySQL 模式）
-- 列名使用下划线风格，与 MyBatis Plus 生成的 SQL 一致
-- 约束与生产库保持一致

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_name` VARCHAR(256) DEFAULT NULL,
    `user_account` VARCHAR(256) NOT NULL,
    `user_avatar` VARCHAR(1024) DEFAULT NULL,
    `gender` TINYINT DEFAULT NULL,
    `user_role` VARCHAR(256) NOT NULL DEFAULT 'user',
    `user_password` VARCHAR(512) NOT NULL,
    `access_key` VARCHAR(256) DEFAULT NULL,
    `secret_key` VARCHAR(256) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    UNIQUE (`user_account`)
);

CREATE TABLE IF NOT EXISTS `interface_info` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) DEFAULT NULL,
    `sdk_method_name` VARCHAR(128) DEFAULT NULL,
    `description` VARCHAR(512) DEFAULT NULL,
    `url` VARCHAR(512) DEFAULT NULL,
    `path` VARCHAR(512) DEFAULT NULL,
    `target_host` VARCHAR(512) DEFAULT NULL,
    `request_params` TEXT DEFAULT NULL,
    `request_header` TEXT DEFAULT NULL,
    `response_header` TEXT DEFAULT NULL,
    `status` INT NOT NULL DEFAULT 0,
    `method` VARCHAR(16) DEFAULT NULL,
    `quota_type` VARCHAR(32) NOT NULL DEFAULT 'BASIC_QUOTA',
    `user_id` BIGINT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    UNIQUE (`path`, `method`, `is_delete`)
);

CREATE TABLE IF NOT EXISTS `interface_quota_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `quota_type` VARCHAR(32) NOT NULL,
    `initial_quota` INT NOT NULL DEFAULT 0,
    `description` VARCHAR(256) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    UNIQUE (`quota_type`, `is_delete`)
);

INSERT INTO `interface_quota_config` (`quota_type`, `initial_quota`, `description`)
SELECT 'FREE_UNLIMITED', 0, '免费无限接口'
WHERE NOT EXISTS (SELECT 1 FROM `interface_quota_config` WHERE `quota_type` = 'FREE_UNLIMITED' AND `is_delete` = 0);

INSERT INTO `interface_quota_config` (`quota_type`, `initial_quota`, `description`)
SELECT 'BASIC_QUOTA', 100, '基础额度接口'
WHERE NOT EXISTS (SELECT 1 FROM `interface_quota_config` WHERE `quota_type` = 'BASIC_QUOTA' AND `is_delete` = 0);

INSERT INTO `interface_quota_config` (`quota_type`, `initial_quota`, `description`)
SELECT 'ADVANCED_TRIAL', 3, '高级体验接口'
WHERE NOT EXISTS (SELECT 1 FROM `interface_quota_config` WHERE `quota_type` = 'ADVANCED_TRIAL' AND `is_delete` = 0);

CREATE TABLE IF NOT EXISTS `user_interface_info` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `interface_info_id` BIGINT NOT NULL,
    `total_num` INT NOT NULL DEFAULT 0,
    `left_num` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    UNIQUE (`user_id`, `interface_info_id`)
);

CREATE TABLE IF NOT EXISTS `interface_invoke_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `interface_info_id` BIGINT NOT NULL,
    `path` VARCHAR(512) NOT NULL,
    `method` VARCHAR(16) NOT NULL,
    `status_code` INT DEFAULT NULL,
    `success` TINYINT NOT NULL DEFAULT 0,
    `response_time_ms` BIGINT NOT NULL DEFAULT 0,
    `invoke_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS `user_role_change_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `operator_id` BIGINT NOT NULL,
    `target_user_id` BIGINT NOT NULL,
    `old_role` VARCHAR(256) NOT NULL,
    `new_role` VARCHAR(256) DEFAULT NULL,
    `remark` VARCHAR(512) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
