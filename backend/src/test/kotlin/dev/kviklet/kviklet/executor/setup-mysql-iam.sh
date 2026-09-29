#!/bin/bash
echo "Starting setup of MySQL RDS instance with IAM authentication..."
set -e
export AWS_PAGER=""
# Configuration variables
DB_INSTANCE_IDENTIFIER="test-mysql-iam"
DB_NAME="testdb"
DB_USERNAME="iamdbuser"
INSTANCE_CLASS="db.t3.micro"  # Smallest available instance class
ALLOCATED_STORAGE=20          # Minimum storage in GB
VPC_SECURITY_GROUP_NAME="mysql-iam-test-sg"
AWS_REGION="${AWS_REGION:-$(aws configure get region)}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Function to check if security group exists
security_group_exists() {
    aws ec2 describe-security-groups --group-names $VPC_SECURITY_GROUP_NAME 2>/dev/null
    return $?
}

# Function to check if IAM policy exists
policy_exists() {
    aws iam get-policy --policy-arn "arn:aws:iam::$AWS_ACCOUNT_ID:policy/mysql-iam-test-policy" 2>/dev/null
    return $?
}

# Function to check if IAM role exists
role_exists() {
    aws iam get-role --role-name mysql-iam-test-role 2>/dev/null
    return $?
}

# Function to check if RDS instance exists
rds_instance_exists() {
    aws rds describe-db-instances --db-instance-identifier $DB_INSTANCE_IDENTIFIER 2>/dev/null
    return $?
}

mysql_user_exists() {
    local TEMP_FILE=$(mktemp)
    echo "[client]
user=admin
password=$1" > "$TEMP_FILE"
    chmod 600 "$TEMP_FILE"

    mysql --defaults-extra-file="$TEMP_FILE" -h "$2" -P 3306 -e "SELECT User FROM mysql.user WHERE User='$DB_USERNAME'" 2>/dev/null | grep -q "$DB_USERNAME"
    local RESULT=$?

    rm "$TEMP_FILE"
    return $RESULT
}

get_current_user() {
    aws iam get-user --query 'User.UserName' --output text
}

policy_attached_to_current_user() {
    local CURRENT_USER=$(get_current_user)
    aws iam list-attached-user-policies --user-name $CURRENT_USER \
        --query "AttachedPolicies[?PolicyArn=='$1'].PolicyArn" \
        --output text | grep -q "$1"
    return $?
}
echo "Checking and creating security group..."
if ! security_group_exists; then
  echo "Creating security group..."
  VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query "Vpcs[0].VpcId" --output text)
  SECURITY_GROUP_ID=$(aws ec2 create-security-group \
      --group-name $VPC_SECURITY_GROUP_NAME \
      --description "Security group for MySQL RDS IAM testing" \
      --vpc-id $VPC_ID \
      --query 'GroupId' \
      --output text)

  # Allow inbound MySQL traffic from anywhere (for testing purposes)
  aws ec2 authorize-security-group-ingress \
      --group-id $SECURITY_GROUP_ID \
      --protocol tcp \
      --port 3306 \
      --cidr 0.0.0.0/0
else
    echo "Security group already exists"
    SECURITY_GROUP_ID=$(aws ec2 describe-security-groups --group-names $VPC_SECURITY_GROUP_NAME --query 'SecurityGroups[0].GroupId' --output text)
fi

echo "Checking and creating IAM policy..."
if ! policy_exists; then
  echo "Creating IAM policy..."
  IAM_POLICY_ARN=$(aws iam create-policy \
      --policy-name mysql-iam-test-policy \
      --policy-document '{
          "Version": "2012-10-17",
          "Statement": [
              {
                  "Effect": "Allow",
                  "Action": [
                      "rds-db:connect"
                  ],
                  "Resource": [
                      "arn:aws:rds-db:'$AWS_REGION':'$AWS_ACCOUNT_ID':dbuser:*/'$DB_USERNAME'"
                  ]
              }
          ]
      }' \
      --query 'Policy.Arn' \
      --output text)
else
    echo "IAM policy already exists"
    IAM_POLICY_ARN="arn:aws:iam::$AWS_ACCOUNT_ID:policy/mysql-iam-test-policy"
fi

echo "Checking if policy is attached to current user..."
CURRENT_USER=$(get_current_user)
if ! policy_attached_to_current_user "$IAM_POLICY_ARN"; then
    echo "Attaching policy to current user..."
    aws iam attach-user-policy \
        --user-name $CURRENT_USER \
        --policy-arn $IAM_POLICY_ARN
    echo "Policy attached successfully"
else
    echo "Policy already attached to current user"
fi

echo "Checking and creating IAM role..."
if ! role_exists; then
  echo "Creating IAM role..."
  IAM_ROLE_ARN=$(aws iam create-role \
      --role-name mysql-iam-test-role \
      --assume-role-policy-document '{
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
      }' \
      --query 'Role.Arn' \
      --output text)

  echo "Attaching policy to role..."
  aws iam attach-role-policy \
      --role-name mysql-iam-test-role \
      --policy-arn $IAM_POLICY_ARN
else
    echo "IAM role already exists"
fi

echo "Checking and creating RDS instance..."
if ! rds_instance_exists; then
  echo "Creating RDS instance..."
  MASTER_PASSWORD=$(openssl rand -base64 15 | tr -dc 'a-zA-Z0-9!@#$%^&*()' | head -c 20)A1!
  echo "::add-mask::$MASTER_PASSWORD"
  aws rds create-db-instance \
      --db-instance-identifier $DB_INSTANCE_IDENTIFIER \
      --db-instance-class $INSTANCE_CLASS \
      --engine mysql \
      --engine-version "8.0.34" \
      --allocated-storage $ALLOCATED_STORAGE \
      --master-username admin \
      --master-user-password "$MASTER_PASSWORD" \
      --vpc-security-group-ids $SECURITY_GROUP_ID \
      --enable-iam-database-authentication \
      --publicly-accessible \
      --backup-retention-period 0 \
      --deletion-protection false \
      --skip-final-snapshot \
      --db-name $DB_NAME

  echo "Waiting for RDS instance to be available..."
  aws rds wait db-instance-available \
      --db-instance-identifier $DB_INSTANCE_IDENTIFIER
else
    echo "RDS instance already exists"
    # We need to get the master password for an existing instance
    # You might want to store this securely somewhere or require it as input
    read -p "Please enter the master password for the existing RDS instance: " MASTER_PASSWORD
fi

# Get the RDS endpoint
DB_ENDPOINT=$(aws rds describe-db-instances \
    --db-instance-identifier $DB_INSTANCE_IDENTIFIER \
    --query 'DBInstances[0].Endpoint.Address' \
    --output text)

echo "Checking and creating IAM database user..."
if ! mysql_user_exists "$MASTER_PASSWORD" "$DB_ENDPOINT"; then
  echo "Creating IAM database user..."
  # Save master password to temp file for mysql client
  echo "[client]
password=$MASTER_PASSWORD" > /tmp/mysql_pwd.cnf
  chmod 600 /tmp/mysql_pwd.cnf

  # Create IAM user in MySQL
  mysql --defaults-extra-file=/tmp/mysql_pwd.cnf -h $DB_ENDPOINT -P 3306 -u admin << EOF
CREATE USER '$DB_USERNAME' IDENTIFIED WITH AWSAuthenticationPlugin AS 'RDS';
GRANT ALL PRIVILEGES ON *.* TO '$DB_USERNAME'@'%';
FLUSH PRIVILEGES;
EOF

  # Clean up password file
  rm /tmp/mysql_pwd.cnf
  echo "IAM database user created successfully"
else
    echo "IAM database user already exists"
fi

echo "Setup complete!"
echo "RDS Endpoint: $DB_ENDPOINT"
echo "Database Name: $DB_NAME"
echo "IAM Username: $DB_USERNAME"
echo
echo "Add these values to your application.yml or environment variables:"
echo "aws.db.host=$DB_ENDPOINT"

# Cleanup instructions
echo
echo "To clean up resources when done, run:"
echo "aws rds delete-db-instance --db-instance-identifier $DB_INSTANCE_IDENTIFIER --skip-final-snapshot"
echo "aws ec2 delete-security-group --group-id $SECURITY_GROUP_ID"
echo "aws iam detach-role-policy --role-name mysql-iam-test-role --policy-arn $IAM_POLICY_ARN"
echo "aws iam delete-policy --policy-arn $IAM_POLICY_ARN"
echo "aws iam delete-role --role-name mysql-iam-test-role"