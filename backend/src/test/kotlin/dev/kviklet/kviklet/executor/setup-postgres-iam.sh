#!/bin/bash
set -e
export AWS_PAGER=""

# Configuration variables
DB_INSTANCE_IDENTIFIER_PG="test-postgres-iam"
DB_NAME_PG="postgres" # Default database for PostgreSQL, as used in PostgresIAMAuthExecutorTest.kt
DB_USERNAME_PG="iamdbuser" # As used in PostgresIAMAuthExecutorTest.kt
INSTANCE_CLASS_PG="db.t3.micro" # Smallest available instance class
ALLOCATED_STORAGE_PG=20        # Minimum storage in GB
VPC_SECURITY_GROUP_NAME_PG="postgres-iam-test-sg"
IAM_POLICY_NAME_PG="postgres-iam-test-policy"
IAM_ROLE_NAME_PG="postgres-iam-auth-role" # Changed from mysql-iam-test-role
AWS_REGION=$(aws configure get region)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IAM_POLICY_ARN_PG="arn:aws:iam::$AWS_ACCOUNT_ID:policy/$IAM_POLICY_NAME_PG"

# Function to check if security group exists
security_group_exists_pg() {
    aws ec2 describe-security-groups --group-names "$VPC_SECURITY_GROUP_NAME_PG" 2>/dev/null
    return $?
}

# Function to check if IAM policy exists
policy_exists_pg() {
    aws iam get-policy --policy-arn "$IAM_POLICY_ARN_PG" 2>/dev/null
    return $?
}

# Function to check if IAM role exists
role_exists_pg() {
    aws iam get-role --role-name "$IAM_ROLE_NAME_PG" 2>/dev/null
    return $?
}

# Function to check if RDS instance exists
rds_instance_exists_pg() {
    aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG" 2>/dev/null
    return $?
}

# Function to check if PostgreSQL user exists
postgres_user_exists() {
    local master_password="$1"
    local db_host="$2"
    
    export PGPASSWORD="$master_password"
    psql -h "$db_host" -U admin -d "$DB_NAME_PG" -tAc "SELECT 1 FROM pg_roles WHERE rolname=\'$DB_USERNAME_PG\'" 2>/dev/null | grep -q "1"
    local RESULT=$?
    unset PGPASSWORD
    return $RESULT
}

get_current_user() {
    aws iam get-user --query \'User.UserName\' --output text 2>/dev/null || aws sts get-caller-identity --query Arn --output text | awk -F \'/\' \'{print $NF}\'
}

policy_attached_to_current_user_pg() {
    local current_user
    current_user=$(get_current_user)
    aws iam list-attached-user-policies --user-name "$current_user" \
        --query "AttachedPolicies[?PolicyArn==\'$1\'].PolicyArn" \
        --output text | grep -q "$1"
    return $?
}

echo "Checking and creating PostgreSQL security group ($VPC_SECURITY_GROUP_NAME_PG)..."
if ! security_group_exists_pg; then
  echo "Creating security group $VPC_SECURITY_GROUP_NAME_PG..."
  VPC_ID_PG=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query "Vpcs[0].VpcId" --output text)
  SECURITY_GROUP_ID_PG=$(aws ec2 create-security-group \
      --group-name "$VPC_SECURITY_GROUP_NAME_PG" \
      --description "Security group for PostgreSQL RDS IAM testing" \
      --vpc-id "$VPC_ID_PG" \
      --query \'GroupId\' \
      --output text)

  aws ec2 authorize-security-group-ingress \
      --group-id "$SECURITY_GROUP_ID_PG" \
      --protocol tcp \
      --port 5432 \
      --cidr 0.0.0.0/0
else
    echo "Security group $VPC_SECURITY_GROUP_NAME_PG already exists"
    SECURITY_GROUP_ID_PG=$(aws ec2 describe-security-groups --group-names "$VPC_SECURITY_GROUP_NAME_PG" --query \'SecurityGroups[0].GroupId\' --output text)
fi

echo "Checking and creating PostgreSQL IAM policy ($IAM_POLICY_NAME_PG)..."
if ! policy_exists_pg; then
  echo "Creating IAM policy $IAM_POLICY_NAME_PG..."
  aws iam create-policy \
      --policy-name "$IAM_POLICY_NAME_PG" \
      --policy-document "{
          \"Version\": \"2012-10-17\",
          \"Statement\": [
              {
                  \"Effect\": \"Allow\",
                  \"Action\": [
                      \"rds-db:connect\"
                  ],
                  \"Resource\": [
                      \"arn:aws:rds-db:$AWS_REGION:$AWS_ACCOUNT_ID:dbuser:$DB_INSTANCE_IDENTIFIER_PG/$DB_USERNAME_PG\"
                  ]
              }
          ]
      }" \
      --query \'Policy.Arn\' \
      --output text
else
    echo "IAM policy $IAM_POLICY_NAME_PG already exists"
fi

echo "Checking if policy $IAM_POLICY_NAME_PG is attached to current user..."
CURRENT_USER_PG=$(get_current_user)
if ! policy_attached_to_current_user_pg "$IAM_POLICY_ARN_PG"; then
    echo "Attaching policy $IAM_POLICY_NAME_PG to current user $CURRENT_USER_PG..."
    aws iam attach-user-policy \
        --user-name "$CURRENT_USER_PG" \
        --policy-arn "$IAM_POLICY_ARN_PG"
    echo "Policy $IAM_POLICY_NAME_PG attached successfully to $CURRENT_USER_PG"
else
    echo "Policy $IAM_POLICY_NAME_PG already attached to current user $CURRENT_USER_PG"
fi

echo "Checking and creating PostgreSQL IAM role ($IAM_ROLE_NAME_PG)..."
if ! role_exists_pg; then
  echo "Creating IAM role $IAM_ROLE_NAME_PG..."
  aws iam create-role \
      --role-name "$IAM_ROLE_NAME_PG" \
      --assume-role-policy-document \'{
          "Version": "2012-10-17",
          "Statement": [
              {
                  "Effect": "Allow",
                  "Principal": {
                      "Service": "rds.amazonaws.com"
                  },
                  "Action": "sts:AssumeRole"
              }
          ]
      }\' \
      --query \'Role.Arn\' \
      --output text

  echo "Attaching policy $IAM_POLICY_NAME_PG to role $IAM_ROLE_NAME_PG..."
  aws iam attach-role-policy \
      --role-name "$IAM_ROLE_NAME_PG" \
      --policy-arn "$IAM_POLICY_ARN_PG"
else
    echo "IAM role $IAM_ROLE_NAME_PG already exists"
fi

echo "Checking and creating PostgreSQL RDS instance ($DB_INSTANCE_IDENTIFIER_PG)..."
if ! rds_instance_exists_pg; then
  echo "Creating RDS instance $DB_INSTANCE_IDENTIFIER_PG..."
  # Generate a strong master password
  MASTER_PASSWORD_PG=$(openssl rand -base64 15 | tr -dc \'a-zA-Z0-9!@#$%^&*()\' | head -c 20)A1!
  echo "Master password for $DB_INSTANCE_IDENTIFIER_PG will be generated."

  aws rds create-db-instance \
      --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG" \
      --db-instance-class "$INSTANCE_CLASS_PG" \
      --engine postgres \
      --engine-version "15.5" \
      --allocated-storage "$ALLOCATED_STORAGE_PG" \
      --master-username admin \
      --master-user-password "$MASTER_PASSWORD_PG" \
      --vpc-security-group-ids "$SECURITY_GROUP_ID_PG" \
      --enable-iam-database-authentication \
      --publicly-accessible \
      --backup-retention-period 0 \
      --db-name "$DB_NAME_PG"

  echo "Waiting for RDS instance $DB_INSTANCE_IDENTIFIER_PG to be available..."
  aws rds wait db-instance-available \
      --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG"
  
  # Store password for user creation step, will be deleted
  echo "$MASTER_PASSWORD_PG" > "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt"
else
    echo "RDS instance $DB_INSTANCE_IDENTIFIER_PG already exists."
    # For existing instances, we need the password.
    # In an automated CI environment, this branch might need adjustment,
    # e.g. fail if exists, or retrieve password from a secure store.
    if [ ! -f "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt" ]; then
        read -rsp "Please enter the master password for the existing RDS instance $DB_INSTANCE_IDENTIFIER_PG: " MASTER_PASSWORD_PG_INPUT
        echo "$MASTER_PASSWORD_PG_INPUT" > "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt"
        echo
    fi
fi

DB_ENDPOINT_PG=$(aws rds describe-db-instances \
    --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG" \
    --query \'DBInstances[0].Endpoint.Address\' \
    --output text)

MASTER_PASSWORD_PG_FROM_FILE=$(cat "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt")

echo "Checking and creating IAM database user $DB_USERNAME_PG..."
if ! postgres_user_exists "$MASTER_PASSWORD_PG_FROM_FILE" "$DB_ENDPOINT_PG"; then
  echo "Creating IAM database user $DB_USERNAME_PG in PostgreSQL instance $DB_INSTANCE_IDENTIFIER_PG..."
  export PGPASSWORD="$MASTER_PASSWORD_PG_FROM_FILE"
  psql -h "$DB_ENDPOINT_PG" -U admin -d "$DB_NAME_PG" <<-EOSQL
    CREATE USER "$DB_USERNAME_PG";
    GRANT rds_iam TO "$DB_USERNAME_PG";
EOSQL
  unset PGPASSWORD
  echo "IAM database user $DB_USERNAME_PG created successfully."
else
    echo "IAM database user $DB_USERNAME_PG already exists."
fi

# Clean up temporary password file
if [ -f "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt" ]; then
    rm "/tmp/pg_master_pwd_${DB_INSTANCE_IDENTIFIER_PG}.txt"
fi

echo "PostgreSQL Setup complete!"
echo "RDS Endpoint: $DB_ENDPOINT_PG"
echo "Database Name: $DB_NAME_PG"
echo "IAM Username: $DB_USERNAME_PG"
echo
echo "To be used in GitHub Actions or environment:"
echo "POSTGRES_HOST_OUTPUT=$DB_ENDPOINT_PG"
echo "::set-output name=postgres_host::$DB_ENDPOINT_PG" # For GitHub Actions

# Cleanup instructions (for manual use if needed)
echo
echo "To clean up PostgreSQL resources when done, run the cleanup script or manually:"
echo "aws rds delete-db-instance --db-instance-identifier $DB_INSTANCE_IDENTIFIER_PG --skip-final-snapshot --delete-automated-backups"
echo "aws ec2 delete-security-group --group-id $SECURITY_GROUP_ID_PG"
# Detach user policy if attached: aws iam detach-user-policy --user-name YOUR_IAM_USER --policy-arn $IAM_POLICY_ARN_PG
echo "aws iam detach-role-policy --role-name $IAM_ROLE_NAME_PG --policy-arn $IAM_POLICY_ARN_PG"
echo "aws iam delete-role --role-name $IAM_ROLE_NAME_PG"
echo "aws iam delete-policy --policy-arn $IAM_POLICY_ARN_PG" 