-- Migration script to convert description columns from VARCHAR(255) to TEXT
-- This migration is safe to run on existing databases and will preserve all existing data
-- Run this script if you are upgrading from a version where description fields were limited to 255 characters

-- Convert execution_request.description from VARCHAR(255) to TEXT
ALTER TABLE execution_request ALTER COLUMN description TYPE TEXT;

-- Convert connection.description from VARCHAR(255) to TEXT
ALTER TABLE connection ALTER COLUMN description TYPE TEXT;

-- Convert role.description from VARCHAR(255) to TEXT
ALTER TABLE role ALTER COLUMN description TYPE TEXT;
