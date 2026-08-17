#!/bin/bash
set -e
export AWS_PAGER=""

# Configuration variables
DB_INSTANCE_IDENTIFIER="test-mysql-iam"
VPC_SECURITY_GROUP_NAME="mysql-iam-test-sg"
AWS_REGION=$(aws configure get region)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IAM_POLICY_ARN="arn:aws:iam::$AWS_ACCOUNT_ID:policy/mysql-iam-test-policy"

echo "Starting cleanup of all resources..."

# Function to check if RDS instance exists
rds_instance_exists() {
    aws rds describe-db-instances --db-instance-identifier $DB_INSTANCE_IDENTIFIER 2>/dev/null
    return $?
}

# Function to check if security group exists
security_group_exists() {
    aws ec2 describe-security-groups --group-names $VPC_SECURITY_GROUP_NAME 2>/dev/null
    return $?
}

# Function to check if IAM role exists
role_exists() {
    aws iam get-role --role-name mysql-iam-test-role 2>/dev/null
    return $?
}

# Function to check if IAM policy exists
policy_exists() {
    aws iam get-policy --policy-arn $IAM_POLICY_ARN 2>/dev/null
    return $?
}

CURRENT_USER=$(aws sts get-caller-identity --output json --query Arn)

USERNAME=$(aws iam get-user --query User.UserName --output text)
aws iam detach-user-policy --user-name $USERNAME --policy-arn $IAM_POLICY_ARN


# Delete RDS instance
if rds_instance_exists; then
    echo "Deleting RDS instance..."
    aws rds delete-db-instance \
        --db-instance-identifier $DB_INSTANCE_IDENTIFIER \
        --skip-final-snapshot \
        --delete-automated-backups

    echo "Waiting for RDS instance to be deleted..."
    aws rds wait db-instance-deleted \
        --db-instance-identifier $DB_INSTANCE_IDENTIFIER
else
    echo "RDS instance does not exist, skipping..."
fi

# Delete security group
if security_group_exists; then
    echo "Deleting security group..."
    SECURITY_GROUP_ID=$(aws ec2 describe-security-groups \
        --group-names $VPC_SECURITY_GROUP_NAME \
        --query 'SecurityGroups[0].GroupId' \
        --output text)
    aws ec2 delete-security-group --group-id $SECURITY_GROUP_ID
else
    echo "Security group does not exist, skipping..."
fi

# Detach and delete IAM role
if role_exists; then
    echo "Detaching policy from role..."
    aws iam detach-role-policy \
        --role-name mysql-iam-test-role \
        --policy-arn $IAM_POLICY_ARN

    echo "Deleting IAM role..."
    aws iam delete-role --role-name mysql-iam-test-role
else
    echo "IAM role does not exist, skipping..."
fi

# Delete IAM policy
if policy_exists; then
    echo "Deleting IAM policy..."
    aws iam delete-policy --policy-arn $IAM_POLICY_ARN
else
    echo "IAM policy does not exist, skipping..."
fi



echo "Cleanup complete!"