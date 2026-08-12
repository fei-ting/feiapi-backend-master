SET NAMES utf8mb4;

-- 一次性迁移：归档历史默认值，清理响应字段无意义的示例值/校验规则，并删除默认值列。
CREATE TABLE IF NOT EXISTS feiapi.interface_doc_param_default_value_archive
(
    `id` bigint not null primary key,
    `interface_info_id` bigint not null,
    `param_scene` varchar(32) not null,
    `default_value` varchar(512) null,
    `archived_at` datetime not null default CURRENT_TIMESTAMP
) comment '接口文档参数默认值历史归档';

CREATE TABLE IF NOT EXISTS feiapi.interface_doc_response_metadata_archive
(
    `id` bigint not null primary key,
    `interface_info_id` bigint not null,
    `example_value` varchar(1024) null,
    `validation_rule` varchar(512) null,
    `archived_at` datetime not null default CURRENT_TIMESTAMP
) comment '接口文档响应字段历史元数据归档';

SET @default_value_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'interface_doc_param'
      AND column_name = 'default_value'
);

SET @archive_default_value_sql := IF(
    @default_value_column_exists > 0,
    'INSERT INTO feiapi.interface_doc_param_default_value_archive (`id`, `interface_info_id`, `param_scene`, `default_value`) SELECT p.`id`, p.`interface_info_id`, p.`param_scene`, p.`default_value` FROM feiapi.interface_doc_param p WHERE NOT EXISTS (SELECT 1 FROM feiapi.interface_doc_param_default_value_archive a WHERE a.`id` = p.`id`)',
    'SELECT 1'
);
PREPARE archive_default_value_statement FROM @archive_default_value_sql;
EXECUTE archive_default_value_statement;
DEALLOCATE PREPARE archive_default_value_statement;

INSERT INTO feiapi.interface_doc_response_metadata_archive
    (`id`, `interface_info_id`, `example_value`, `validation_rule`)
SELECT p.`id`, p.`interface_info_id`, p.`example_value`, p.`validation_rule`
FROM feiapi.interface_doc_param p
WHERE p.`param_scene` = 'RESPONSE'
  AND NOT EXISTS (
      SELECT 1 FROM feiapi.interface_doc_response_metadata_archive a WHERE a.`id` = p.`id`
  );

UPDATE feiapi.interface_doc_param
SET `example_value` = NULL,
    `validation_rule` = NULL
WHERE `param_scene` = 'RESPONSE';

SET @drop_default_value_sql := IF(
    @default_value_column_exists > 0,
    'ALTER TABLE feiapi.interface_doc_param DROP COLUMN `default_value`',
    'SELECT 1'
);
PREPARE drop_default_value_statement FROM @drop_default_value_sql;
EXECUTE drop_default_value_statement;
DEALLOCATE PREPARE drop_default_value_statement;
