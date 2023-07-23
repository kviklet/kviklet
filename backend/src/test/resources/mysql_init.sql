/* CLEAN UP */
DROP SCHEMA IF EXISTS foo;

/* INIT */
CREATE SCHEMA foo;
USE foo;
CREATE TABLE simple_table (
    col1 INT,
    col2 VARCHAR(255)
);
INSERT INTO simple_table(col1, col2) VALUES
    (1, 'foo'),
    (2, 'bar')
;
CREATE TABLE `all_datatypes` (
    `int_column` INT,
    `tinyint_column` TINYINT,
    `smallint_column` SMALLINT,
    `mediumint_column` MEDIUMINT,
    `bigint_column` BIGINT,
    `float_column` FLOAT,
    `double_column` DOUBLE PRECISION,
    `decimal_column` DECIMAL(10,2),
    `date_column` DATE,
    `datetime_column` DATETIME(6),
    `timestamp_column` TIMESTAMP(6),
    `time_column` TIME(6),
    `year_column` YEAR(4),
    `char_column` CHAR(255),
    `varchar_column` VARCHAR(255),
    `binary_column` BINARY(255),
    `varbinary_column` VARBINARY(255),
    `tinyblob_column` TINYBLOB,
    `blob_column` BLOB,
    `mediumblob_column` MEDIUMBLOB,
    `longblob_column` LONGBLOB,
    `tinytext_column` TINYTEXT,
    `text_column` TEXT,
    `mediumtext_column` MEDIUMTEXT,
    `longtext_column` LONGTEXT,
    `enum_column` ENUM('option1', 'option2'),
    `set_column` SET('option1', 'option2'),
    `bit_column` BIT(8),
    `bool_column` BOOL,
    `json_column` JSON
);
INSERT INTO `all_datatypes` VALUES (
    1,
    1,
    1,
    1,
    1,
    1.23,
    1.23,
    1.23,
    '2023-01-01',
    '2023-01-01 00:00:00.000000',
    '2023-01-01 00:00:00.000000',
    '00:00:00.000000',
    2023,
    'char',
    'varchar',
    b'101010',
    b'101010',
    'tinyblob',
    'blob',
    'mediumblob',
    'longblob',
    'tinytext',
    'text',
    'mediumtext',
    'longtext',
    'option1',
    'option1',
    b'10101010',
    TRUE,
    '{"key": "value"}'
);
