/* CLEAN UP */
DROP SCHEMA IF EXISTS foo CASCADE;

/* INIT */
CREATE SCHEMA foo;
SET SCHEMA 'foo';
CREATE TABLE simple_table (
    col1 INT,
    col2 VARCHAR(255)
);
INSERT INTO simple_table(col1, col2) VALUES
    (1, 'foo'),
    (2, 'bar')
;
CREATE TABLE all_datatypes (
    int_column INT,
    smallint_column SMALLINT,
    bigint_column BIGINT,
    real_column REAL,
    double_column DOUBLE PRECISION,
    decimal_column DECIMAL(10,2),
    numeric_column NUMERIC(10,2),
    boolean_column BOOLEAN,
    char_column CHAR(255),
    varchar_column VARCHAR(255),
    text_column TEXT,
    name_column NAME,
    bytea_column BYTEA,
    bit_column BIT(8),
    bit_varying_column BIT VARYING(8),
    timestamp_column TIMESTAMP,
    interval_column INTERVAL,
    date_column DATE,
    time_column TIME,
    timez_column TIMETZ,
    money_column MONEY,
    uuid_column UUID,
    cidr_column CIDR,
    inet_column INET,
    macaddr_column MACADDR,
    macaddr8_column MACADDR8,
    json_column JSON,
    jsonb_column JSONB,
    xml_column XML,
    point_column POINT,
    line_column LINE,
    --lseg_column LSEG,
    box_column BOX,
    path_column PATH,
    polygon_column POLYGON,
    circle_column CIRCLE,
    tsvector_column TSVECTOR,
    tsquery_column TSQUERY
);
INSERT INTO all_datatypes VALUES (
    1,
    1,
    1,
    1.23,
    1.23,
    1.23,
    1.23,
    TRUE,
    'char',
    'varchar',
    'text',
    'name',
    E'\\xDEADBEEF',
    B'10101010',
    B'10101010',
    '2023-01-01 00:00:00',
    '1 year 2 months 3 days 4 hours 5 minutes 6 seconds',
    '2023-01-01',
    '00:00:00',
    '00:00:00+00',
    1234.56,
    'b3bae92c-3c3b-11ec-8d3d-0242ac130003',
    '192.168.100.128/25',
    '192.168.100.128',
    '08:00:2b:01:02:03',
    '08:00:2b:01:02:03:04:05',
    '{"key": "value"}',
    '{"key": "value"}',
    '<root></root>',
    '(0,0)',
    --'{1, -1}',
    '[(0,0),(1,1)]',
    '((0,0),(1,1))',
    '[(0,0), (1,1)]',
    '((0,0), (1,1))',
    '<(0,0),1>',
    'simple',
    '''simple'''
);
