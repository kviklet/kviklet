#!/bin/bash
set -e
export AWS_PAGER=""

# Configuration variables from setup-postgres-iam.sh
DB_INSTANCE_IDENTIFIER_PG="test-postgres-iam"
VPC_SECURITY_GROUP_NAME_PG="postgres-iam-test-sg"
IAM_POLICY_NAME_PG="postgres-iam-test-policy"
IAM_ROLE_NAME_PG="postgres-iam-auth-role"
AWS_REGION=$(aws configure get region)
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
IAM_POLICY_ARN_PG="arn:aws:iam::$AWS_ACCOUNT_ID:policy/$IAM_POLICY_NAME_PG"

echo "Starting cleanup of all PostgreSQL test resources..."

# Function to check if RDS instance exists
rds_instance_exists_pg() {
    aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG" 2>/dev/null
    return $?
}

# Function to check if security group exists
security_group_exists_pg() {
    aws ec2 describe-security-groups --group-names "$VPC_SECURITY_GROUP_NAME_PG" 2>/dev/null
    return $?
}

# Function to check if IAM role exists
role_exists_pg() {
    aws iam get-role --role-name "$IAM_ROLE_NAME_PG" 2>/dev/null
    return $?
}

# Function to check if IAM policy exists
policy_exists_pg() {
    aws iam get-policy --policy-arn "$IAM_POLICY_ARN_PG" 2>/dev/null
    return $?
}

get_current_user() {
    aws iam get-user --query \'User.UserName\' --output text 2>/dev/null || aws sts get-caller-identity --query Arn --output text | awk -F \'/\' \'{print $NF}\'
}

# Detach policy from current user if it was attached
CURRENT_USER_PG=$(get_current_user)
if aws iam list-attached-user-policies --user-name "$CURRENT_USER_PG" --query "AttachedPolicies[?PolicyArn==\'$IAM_POLICY_ARN_PG\'].PolicyArn" --output text | grep -q "$IAM_POLICY_ARN_PG"; then
    echo "Detaching policy $IAM_POLICY_NAME_PG from user $CURRENT_USER_PG..."
    aws iam detach-user-policy --user-name "$CURRENT_USER_PG" --policy-arn "$IAM_POLICY_ARN_PG"
else
    echo "Policy $IAM_POLICY_NAME_PG not attached to user $CURRENT_USER_PG, skipping detachment."
fi

# Delete RDS instance
if rds_instance_exists_pg; then
    echo "Deleting PostgreSQL RDS instance $DB_INSTANCE_IDENTIFIER_PG..."
    aws rds delete-db-instance \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG" \
        --skip-final-snapshot \
        --delete-automated-backups

    echo "Waiting for PostgreSQL RDS instance $DB_INSTANCE_IDENTIFIER_PG to be deleted..."
    aws rds wait db-instance-deleted \
        --db-instance-identifier "$DB_INSTANCE_IDENTIFIER_PG"
else
    echo "PostgreSQL RDS instance $DB_INSTANCE_IDENTIFIER_PG does not exist, skipping..."
fi

# Delete security group
if security_group_exists_pg; then
    echo "Deleting security group $VPC_SECURITY_GROUP_NAME_PG..."
    SECURITY_GROUP_ID_PG=$(aws ec2 describe-security-groups \
        --group-names "$VPC_SECURITY_GROUP_NAME_PG" \
        --query 'SecurityGroups[0].GroupId' \
        --output text 2>/dev/null || echo "notfound")
    if [ "$SECURITY_GROUP_ID_PG" != "notfound" ] && [ -n "$SECURITY_GROUP_ID_PG" ]; then
        aws ec2 delete-security-group --group-id "$SECURITY_GROUP_ID_PG"
    else
        echo "Could not find Security Group ID for $VPC_SECURITY_GROUP_NAME_PG, skipping deletion."
    fi
else
    echo "Security group $VPC_SECURITY_GROUP_NAME_PG does not exist, skipping..."
fi

# Detach and delete IAM role
if role_exists_pg; then
    echo "Detaching policy $IAM_POLICY_NAME_PG from role $IAM_ROLE_NAME_PG..."
    # Detach all policies from role before deleting, just in case
    ATTACHED_POLICIES=$(aws iam list-attached-role-policies --role-name "$IAM_ROLE_NAME_PG" --query 'AttachedPolicies[*].PolicyArn' --output text)
    for POLICY_ARN in $ATTACHED_POLICIES; do
        echo "Detaching $POLICY_ARN from $IAM_ROLE_NAME_PG"
        aws iam detach-role-policy \
            --role-name "$IAM_ROLE_NAME_PG" \
            --policy-arn "$POLICY_ARN"
    done

    echo "Deleting IAM role $IAM_ROLE_NAME_PG..."
    aws iam delete-role --role-name "$IAM_ROLE_NAME_PG"
else
    echo "IAM role $IAM_ROLE_NAME_PG does not exist, skipping..."
fi

# Delete IAM policy
if policy_exists_pg; then
    echo "Deleting IAM policy $IAM_POLICY_NAME_PG ($IAM_POLICY_ARN_PG)..."
    # Before deleting a policy, detach it from all entities (users, groups, roles)
    # This step might be intensive if the policy is widely used, but for a test-specific policy, it should be fine.
    POLICY_VERSIONS=$(aws iam list-policy-versions --policy-arn "$IAM_POLICY_ARN_PG" --query 'Versions[?IsDefaultVersion==false].VersionId' --output text)
    for VERSION_ID in $POLICY_VERSIONS; do
        echo "Deleting non-default policy version $VERSION_ID for $IAM_POLICY_ARN_PG"
        aws iam delete-policy-version --policy-arn "$IAM_POLICY_ARN_PG" --version-id "$VERSION_ID"
    done
    aws iam delete-policy --policy-arn "$IAM_POLICY_ARN_PG"
else
    echo "IAM policy $IAM_POLICY_NAME_PG ($IAM_POLICY_ARN_PG) does not exist, skipping..."
fi

echo "PostgreSQL cleanup complete!" 