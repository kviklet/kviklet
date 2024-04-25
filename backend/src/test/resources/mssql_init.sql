-- CLEAN UP
DROP TABLE IF EXISTS foo.simple_table;
DROP TABLE IF EXISTS foo.all_datatypes;
DROP TABLE IF EXISTS foo.temp;
DROP SCHEMA IF EXISTS foo;

-- INIT
CREATE SCHEMA foo;


CREATE TABLE foo.simple_table (
                              col1 INT,
                              col2 VARCHAR(255)
);

INSERT INTO foo.simple_table(col1, col2) VALUES
    (1, 'foo'),
    (2, 'bar');

CREATE TABLE foo.all_datatypes (
                               int_column INT,
                               tinyint_column TINYINT,
                               smallint_column SMALLINT,
                               mediumint_column INT, -- SQL Server does not have a MEDIUMINT
                               bigint_column BIGINT,
                               float_column FLOAT,
                               double_column FLOAT, -- SQL Server uses FLOAT for doubles too
                               decimal_column DECIMAL(10,2),
                               date_column DATE,
                               datetime_column DATETIME2(6), -- DATETIME2 for better precision
                               timestamp_column DATETIME2(6), -- SQL Server uses DATETIME2, no auto-update like MySQL TIMESTAMP
                               time_column TIME(6),
                               year_column SMALLINT, -- SQL Server does not have YEAR, using SMALLINT instead
                               char_column CHAR(4),
                               varchar_column VARCHAR(255),
                               binary_column BINARY(255),
                               varbinary_column VARBINARY(255),
                               tinyblob_column VARBINARY(MAX), -- BLOB types in SQL Server are generally VARBINARY(MAX)
                               blob_column VARBINARY(MAX),
                               mediumblob_column VARBINARY(MAX),
                               longblob_column VARBINARY(MAX),
                               tinytext_column VARCHAR(MAX),
    text_column VARCHAR(MAX),
    mediumtext_column VARCHAR(MAX),
    longtext_column VARCHAR(MAX),
    enum_column VARCHAR(50), -- SQL Server does not support ENUM, using VARCHAR instead
    set_column VARCHAR(255), -- SQL Server does not support SET, using VARCHAR instead
    bit_column BIT,
    bool_column BIT, -- SQL Server uses BIT for Boolean types
    json_column NVARCHAR(MAX) -- SQL Server can use NVARCHAR for JSON text, proper JSON support requires JSON functions
);

-- SQL Server does not allow binary string literals or MySQL's ENUM/SET value insertion directly,
-- Instead, use normal string or integer literals that would represent the data you need.
-- Also, binary data should be inserted differently if needed, but in this example, it's omitted for clarity.
INSERT INTO foo.all_datatypes (
    int_column,
    tinyint_column,
    smallint_column,
    mediumint_column,
    bigint_column,
    float_column,
    double_column,
    decimal_column,
    date_column,
    datetime_column,
    timestamp_column,
    time_column,
    year_column,
    char_column,
    varchar_column,
    binary_column,
    varbinary_column,
    tinyblob_column,
    blob_column,
    mediumblob_column,
    longblob_column,
    tinytext_column,
    text_column,
    mediumtext_column,
    longtext_column,
    enum_column,
    set_column,
    bit_column,
    bool_column,
    json_column
) VALUES (
    1,
    1,
    1,
    1,
    1,
    1.23,
    1.23,
    1.23,
    '2023-01-01',
    '2023-01-01 00:00:00',
    '2023-01-01 00:00:00',
    '00:00:00',
    2023,
    'char',
    'varchar',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'tinytext',
    'text',
    'mediumtext',
    'longtext',
    'option1',
    'option1',
    1,
    1,
    '{"key": "value"}'
);
