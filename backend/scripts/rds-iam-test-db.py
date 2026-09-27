#!/usr/bin/env -S uv run --quiet
# /// script
# requires-python = ">=3.11"
# dependencies = ["boto3>=1.35"]
# ///
"""Provision and tear down a throwaway RDS instance with IAM authentication, for testing Kviklet's
AWS IAM connections (JDBC executions and proxied sessions) against the real thing.

    uv run backend/scripts/rds-iam-test-db.py up postgres|mysql|mariadb
    uv run backend/scripts/rds-iam-test-db.py down postgres|mysql|mariadb|all
    uv run backend/scripts/rds-iam-test-db.py status

Credentials come from the repo's .env (AWS_ACCESS_KEY / AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY), or
from the usual AWS environment. Every step is idempotent: re-running `up` reuses whatever already
exists. `up` creates the smallest paid-for footprint that works (db.t4g.micro, 20 GB gp3, no backups,
single AZ), asks before creating the instance, and prints the running cost. Remember to run `down`.

What `up` leaves behind, all named kviklet-iam-test-*:
  - one RDS instance per engine, IAM auth enabled, publicly reachable from THIS machine's IP only
  - a security group, an IAM policy (rds-db:connect for the IAM db user, sts:AssumeRole for the
    role) attached to the calling IAM user, and a role the calling user may assume (for Kviklet's
    role ARN option)
  - inside the database: user `iamdbuser` mapped to IAM, with full rights on `testdb`
The master password is kept in backend/scripts/.rds-iam-test/<engine>.json (gitignored).
"""

from __future__ import annotations

import argparse
import json
import os
import secrets
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import boto3
from botocore.exceptions import ClientError

PREFIX = "kviklet-iam-test"
DB_NAME = "testdb"
MASTER_USER = "kviklet_admin"
IAM_DB_USER = "iamdbuser"
INSTANCE_CLASS = "db.t4g.micro"
STORAGE_GB = 20
DEFAULT_REGION = "eu-central-1"

ENGINES = {
    "postgres": {"engine": "postgres", "version": "16", "port": 5432},
    "mysql": {"engine": "mysql", "version": "8.0", "port": 3306},
    "mariadb": {"engine": "mariadb", "version": "11.4", "port": 3306},
}

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
STATE_DIR = Path(__file__).resolve().parent / ".rds-iam-test"
TAGS = [{"Key": "project", "Value": PREFIX}]


def log(msg: str) -> None:
    print(f"==> {msg}", flush=True)


def die(msg: str) -> None:
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


# --- credentials ----------------------------------------------------------------------------------


def load_env_file(path: Path) -> None:
    """Export KEY=VALUE lines from a .env file. The repo's .env spells the access key AWS_ACCESS_KEY,
    which the SDK does not know, so it is mapped onto AWS_ACCESS_KEY_ID. Values in the file win over
    the ambient environment so a stale ~/.aws/credentials cannot shadow them."""
    if not path.is_file():
        return
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip().strip("'\"")
        if key == "AWS_ACCESS_KEY":
            key = "AWS_ACCESS_KEY_ID"
        os.environ[key] = value


def session_for(args: argparse.Namespace) -> boto3.Session:
    load_env_file(Path(args.env_file))
    region = args.region or os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or DEFAULT_REGION
    if not os.environ.get("AWS_ACCESS_KEY_ID"):
        die(f"no AWS credentials: put AWS_ACCESS_KEY and AWS_SECRET_ACCESS_KEY in {args.env_file}")
    return boto3.Session(region_name=region)


# --- state file -----------------------------------------------------------------------------------


def state_path(engine: str) -> Path:
    return STATE_DIR / f"{engine}.json"


def read_state(engine: str) -> dict:
    p = state_path(engine)
    return json.loads(p.read_text()) if p.is_file() else {}


def write_state(engine: str, state: dict) -> None:
    STATE_DIR.mkdir(exist_ok=True)
    p = state_path(engine)
    p.write_text(json.dumps(state, indent=2) + "\n")
    p.chmod(0o600)


# --- AWS resources --------------------------------------------------------------------------------


def instance_id(engine: str) -> str:
    return f"{PREFIX}-{engine}"


def my_public_ip() -> str:
    with urllib.request.urlopen("https://checkip.amazonaws.com", timeout=10) as r:
        return r.read().decode().strip()


def ensure_security_group(ec2, ports: set[int]) -> str:
    name = f"{PREFIX}-sg"
    groups = ec2.describe_security_groups(Filters=[{"Name": "group-name", "Values": [name]}])["SecurityGroups"]
    if groups:
        sg_id = groups[0]["GroupId"]
        log(f"security group {name} exists ({sg_id})")
    else:
        vpc_id = ec2.describe_vpcs(Filters=[{"Name": "isDefault", "Values": ["true"]}])["Vpcs"][0]["VpcId"]
        sg_id = ec2.create_security_group(
            GroupName=name,
            Description="Kviklet RDS IAM test databases",
            VpcId=vpc_id,
            TagSpecifications=[{"ResourceType": "security-group", "Tags": TAGS}],
        )["GroupId"]
        log(f"created security group {name} ({sg_id}) in {vpc_id}")

    ip = my_public_ip()
    for port in sorted(ports):
        try:
            ec2.authorize_security_group_ingress(
                GroupId=sg_id,
                IpPermissions=[
                    {
                        "IpProtocol": "tcp",
                        "FromPort": port,
                        "ToPort": port,
                        "IpRanges": [{"CidrIp": f"{ip}/32", "Description": "kviklet dev machine"}],
                    }
                ],
            )
            log(f"allowed {ip}/32 -> port {port}")
        except ClientError as e:
            if e.response["Error"]["Code"] != "InvalidPermission.Duplicate":
                raise
            log(f"{ip}/32 -> port {port} already allowed")
    return sg_id


def caller(sts) -> tuple[str, str]:
    identity = sts.get_caller_identity()
    return identity["Account"], identity["Arn"]


def ensure_iam(iam, account: str, region: str, caller_arn: str) -> tuple[str, str]:
    """The connect policy, attached to the calling user, and a role the calling user may assume that
    carries the same policy. Returns (policy_arn, role_arn)."""
    policy_name = f"{PREFIX}-connect"
    role_name = f"{PREFIX}-role"
    policy_arn = f"arn:aws:iam::{account}:policy/{policy_name}"
    role_arn = f"arn:aws:iam::{account}:role/{role_name}"
    document = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": "rds-db:connect",
                "Resource": f"arn:aws:rds-db:{region}:{account}:dbuser:*/{IAM_DB_USER}",
            },
            {"Effect": "Allow", "Action": "sts:AssumeRole", "Resource": role_arn},
        ],
    }
    try:
        iam.get_policy(PolicyArn=policy_arn)
        log(f"policy {policy_name} exists")
    except iam.exceptions.NoSuchEntityException:
        iam.create_policy(PolicyName=policy_name, PolicyDocument=json.dumps(document), Tags=TAGS)
        log(f"created policy {policy_name}")

    if ":user/" in caller_arn:
        user_name = caller_arn.rsplit("/", 1)[1]
        attached = iam.list_attached_user_policies(UserName=user_name)["AttachedPolicies"]
        if any(p["PolicyArn"] == policy_arn for p in attached):
            log(f"policy already attached to user {user_name}")
        else:
            iam.attach_user_policy(UserName=user_name, PolicyArn=policy_arn)
            log(f"attached policy to user {user_name}")
    else:
        log(f"caller {caller_arn} is not an IAM user; not attaching the policy (root/roles are on their own)")

    trust = {
        "Version": "2012-10-17",
        "Statement": [{"Effect": "Allow", "Principal": {"AWS": caller_arn}, "Action": "sts:AssumeRole"}],
    }
    try:
        iam.get_role(RoleName=role_name)
        log(f"role {role_name} exists")
    except iam.exceptions.NoSuchEntityException:
        iam.create_role(
            RoleName=role_name,
            AssumeRolePolicyDocument=json.dumps(trust),
            Description="Kviklet RDS IAM test: assumable by the dev user, may connect as iamdbuser",
            Tags=TAGS,
        )
        log(f"created role {role_name}, assumable by {caller_arn}")
    attached = iam.list_attached_role_policies(RoleName=role_name)["AttachedPolicies"]
    if not any(p["PolicyArn"] == policy_arn for p in attached):
        iam.attach_role_policy(RoleName=role_name, PolicyArn=policy_arn)
        log("attached policy to role")
    return policy_arn, role_arn


def describe_instance(rds, engine: str) -> dict | None:
    try:
        return rds.describe_db_instances(DBInstanceIdentifier=instance_id(engine))["DBInstances"][0]
    except rds.exceptions.DBInstanceNotFoundFault:
        return None


def wait_available(rds, engine: str) -> dict:
    log(f"waiting for {instance_id(engine)} to become available (a fresh instance takes 5-10 minutes)...")
    rds.get_waiter("db_instance_available").wait(
        DBInstanceIdentifier=instance_id(engine), WaiterConfig={"Delay": 20, "MaxAttempts": 90}
    )
    return describe_instance(rds, engine)


def confirm(question: str, assume_yes: bool) -> None:
    if assume_yes:
        return
    answer = input(f"{question} [y/N] ").strip().lower()
    if answer not in ("y", "yes"):
        die("aborted")


def ensure_instance(rds, engine: str, sg_id: str, assume_yes: bool) -> dict:
    spec = ENGINES[engine]
    existing = describe_instance(rds, engine)
    state = read_state(engine)

    if existing is None:
        print()
        print(f"About to create RDS instance {instance_id(engine)}:")
        print(f"  engine    {spec['engine']} {spec['version']}")
        print(f"  class     {INSTANCE_CLASS} (~$0.02/hour while it exists)")
        print(f"  storage   {STORAGE_GB} GB gp3 (~$2.30/month, prorated)")
        print("  backups   none, single AZ, no deletion protection, public from this IP only")
        confirm("Create it?", assume_yes)
        password = secrets.token_urlsafe(24)
        write_state(engine, {"master_password": password})
        rds.create_db_instance(
            DBInstanceIdentifier=instance_id(engine),
            DBName=DB_NAME,
            Engine=spec["engine"],
            EngineVersion=spec["version"],
            DBInstanceClass=INSTANCE_CLASS,
            AllocatedStorage=STORAGE_GB,
            StorageType="gp3",
            MasterUsername=MASTER_USER,
            MasterUserPassword=password,
            VpcSecurityGroupIds=[sg_id],
            EnableIAMDatabaseAuthentication=True,
            PubliclyAccessible=True,
            BackupRetentionPeriod=0,
            MultiAZ=False,
            DeletionProtection=False,
            AutoMinorVersionUpgrade=False,
            Tags=TAGS,
        )
        log("create request accepted")
        return wait_available(rds, engine)

    log(f"instance {instance_id(engine)} exists (status: {existing['DBInstanceStatus']})")
    if existing["DBInstanceStatus"] != "available":
        existing = wait_available(rds, engine)
    if "master_password" not in state:
        # The password only ever lives in the state file; without it the IAM db user cannot be
        # created, so reset it (a reset is instant and harmless on a throwaway instance).
        log("no stored master password for this instance; resetting it")
        password = secrets.token_urlsafe(24)
        rds.modify_db_instance(
            DBInstanceIdentifier=instance_id(engine), MasterUserPassword=password, ApplyImmediately=True
        )
        write_state(engine, {"master_password": password})
        time.sleep(15)
        existing = wait_available(rds, engine)
    return existing


# --- database side --------------------------------------------------------------------------------


def run_sql(engine: str, host: str, port: int, user: str, password: str, sql: str) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    if engine == "postgres":
        env["PGPASSWORD"] = password
        cmd = [
            "psql",
            f"host={host} port={port} dbname={DB_NAME} user={user} sslmode=require",
            "-v",
            "ON_ERROR_STOP=1",
            "-tAq",
            "-c",
            sql,
        ]
    else:
        env["MYSQL_PWD"] = password
        cmd = [
            "mysql",
            f"--host={host}",
            f"--port={port}",
            f"--user={user}",
            "--ssl-mode=REQUIRED",
            "--enable-cleartext-plugin",
            "--batch",
            "--skip-column-names",
            DB_NAME,
            "-e",
            sql,
        ]
    return subprocess.run(cmd, env=env, capture_output=True, text=True)


def ensure_db_user(engine: str, host: str, port: int, master_password: str) -> None:
    if engine == "postgres":
        sql = f"""
            DO $$ BEGIN
                IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '{IAM_DB_USER}') THEN
                    CREATE ROLE {IAM_DB_USER} LOGIN;
                END IF;
            END $$;
            GRANT rds_iam TO {IAM_DB_USER};
            GRANT ALL PRIVILEGES ON DATABASE {DB_NAME} TO {IAM_DB_USER};
            GRANT ALL ON SCHEMA public TO {IAM_DB_USER};
        """
    else:
        sql = f"""
            CREATE USER IF NOT EXISTS '{IAM_DB_USER}'@'%' IDENTIFIED WITH AWSAuthenticationPlugin AS 'RDS';
            GRANT ALL PRIVILEGES ON {DB_NAME}.* TO '{IAM_DB_USER}'@'%';
            FLUSH PRIVILEGES;
        """
    result = run_sql(engine, host, port, MASTER_USER, master_password, sql)
    if result.returncode != 0:
        die(f"creating the IAM db user failed:\n{result.stderr}")
    log(f"db user {IAM_DB_USER} is mapped to IAM auth with full rights on {DB_NAME}")


def verify_iam_login(session: boto3.Session, engine: str, host: str, port: int) -> None:
    """Connect exactly the way Kviklet will: a token from generate_db_auth_token used as the password.
    IAM policy changes take a moment to propagate, so this retries for a minute."""
    rds = session.client("rds")
    deadline = time.time() + 60
    while True:
        token = rds.generate_db_auth_token(DBHostname=host, Port=port, DBUsername=IAM_DB_USER)
        result = run_sql(engine, host, port, IAM_DB_USER, token, "SELECT 1")
        if result.returncode == 0 and result.stdout.strip() == "1":
            log(f"IAM login as {IAM_DB_USER} with a generated token works")
            return
        if time.time() > deadline:
            die(f"IAM login with a generated token keeps failing:\n{result.stderr}")
        time.sleep(5)


# --- commands -------------------------------------------------------------------------------------


def cmd_up(args: argparse.Namespace) -> None:
    engine = args.engine
    spec = ENGINES[engine]
    for tool in ("psql" if engine == "postgres" else "mysql",):
        if shutil.which(tool) is None:
            die(f"{tool} is not on PATH; it is needed to create the IAM db user")
    session = session_for(args)
    region = session.region_name
    account, caller_arn = caller(session.client("sts"))
    log(f"account {account}, caller {caller_arn}, region {region}")

    sg_id = ensure_security_group(session.client("ec2"), {e["port"] for e in ENGINES.values()})
    _, role_arn = ensure_iam(session.client("iam"), account, region, caller_arn)
    instance = ensure_instance(session.client("rds"), engine, sg_id, args.yes)

    host = instance["Endpoint"]["Address"]
    port = instance["Endpoint"]["Port"]
    state = read_state(engine)
    state.update({"host": host, "port": port, "role_arn": role_arn, "region": region})
    write_state(engine, state)

    ensure_db_user(engine, host, port, state["master_password"])
    verify_iam_login(session, engine, host, port)

    print()
    print(f"{engine} is ready (~$0.02/hour until you run `down {engine}`):")
    print(f"  host        {host}")
    print(f"  port        {port}")
    print(f"  database    {DB_NAME}")
    print(f"  iam user    {IAM_DB_USER}")
    print(f"  role arn    {role_arn}")
    print(f"  region      {region}")
    print()
    print("For the aws-integration tagged executor tests (application-local.properties):")
    print(f"  aws.db.{'postgreshost' if engine == 'postgres' else 'mysqlhost'}={host}")
    print()
    print("The backend needs the same credentials in its environment to sign tokens:")
    print(f"  set -a; source {args.env_file}; set +a; export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY AWS_REGION={region}")


def delete_instance(rds, engine: str) -> None:
    if describe_instance(rds, engine) is None:
        log(f"instance {instance_id(engine)} does not exist")
    else:
        try:
            rds.delete_db_instance(
                DBInstanceIdentifier=instance_id(engine), SkipFinalSnapshot=True, DeleteAutomatedBackups=True
            )
            log(f"delete requested for {instance_id(engine)}")
        except rds.exceptions.InvalidDBInstanceStateFault:
            log(f"{instance_id(engine)} is already being deleted")
        log("waiting for the instance to be gone...")
        rds.get_waiter("db_instance_deleted").wait(
            DBInstanceIdentifier=instance_id(engine), WaiterConfig={"Delay": 20, "MaxAttempts": 60}
        )
        log(f"{instance_id(engine)} deleted")
    state_path(engine).unlink(missing_ok=True)


def our_instances(rds) -> list[dict]:
    return [
        i for i in rds.describe_db_instances()["DBInstances"] if i["DBInstanceIdentifier"].startswith(f"{PREFIX}-")
    ]


def delete_shared_resources(session: boto3.Session, account: str, caller_arn: str) -> None:
    iam = session.client("iam")
    ec2 = session.client("ec2")
    policy_arn = f"arn:aws:iam::{account}:policy/{PREFIX}-connect"
    role_name = f"{PREFIX}-role"

    try:
        iam.detach_role_policy(RoleName=role_name, PolicyArn=policy_arn)
    except iam.exceptions.NoSuchEntityException:
        pass
    try:
        iam.delete_role(RoleName=role_name)
        log(f"deleted role {role_name}")
    except iam.exceptions.NoSuchEntityException:
        pass
    if ":user/" in caller_arn:
        try:
            iam.detach_user_policy(UserName=caller_arn.rsplit("/", 1)[1], PolicyArn=policy_arn)
        except iam.exceptions.NoSuchEntityException:
            pass
    try:
        iam.delete_policy(PolicyArn=policy_arn)
        log(f"deleted policy {PREFIX}-connect")
    except iam.exceptions.NoSuchEntityException:
        pass

    groups = ec2.describe_security_groups(Filters=[{"Name": "group-name", "Values": [f"{PREFIX}-sg"]}])
    for group in groups["SecurityGroups"]:
        # The instance's network interface can linger a little after the instance is reported deleted.
        for attempt in range(12):
            try:
                ec2.delete_security_group(GroupId=group["GroupId"])
                log(f"deleted security group {group['GroupId']}")
                break
            except ClientError as e:
                if e.response["Error"]["Code"] != "DependencyViolation" or attempt == 11:
                    raise
                time.sleep(10)


def cmd_down(args: argparse.Namespace) -> None:
    session = session_for(args)
    rds = session.client("rds")
    account, caller_arn = caller(session.client("sts"))
    engines = list(ENGINES) if args.engine == "all" else [args.engine]
    for engine in engines:
        delete_instance(rds, engine)
    remaining = our_instances(rds)
    if remaining:
        log(f"keeping shared IAM/network resources: {', '.join(i['DBInstanceIdentifier'] for i in remaining)} still exist")
    else:
        delete_shared_resources(session, account, caller_arn)
    log("done")


def cmd_status(args: argparse.Namespace) -> None:
    session = session_for(args)
    account, caller_arn = caller(session.client("sts"))
    print(f"account {account}, caller {caller_arn}, region {session.region_name}")
    instances = our_instances(session.client("rds"))
    if not instances:
        print("no kviklet-iam-test instances")
    for i in instances:
        endpoint = i.get("Endpoint", {}).get("Address", "-")
        print(f"  {i['DBInstanceIdentifier']:32} {i['DBInstanceStatus']:12} {i['DBInstanceClass']:14} {endpoint}")
    iam = session.client("iam")
    for kind, check in (
        ("policy", lambda: iam.get_policy(PolicyArn=f"arn:aws:iam::{account}:policy/{PREFIX}-connect")),
        ("role", lambda: iam.get_role(RoleName=f"{PREFIX}-role")),
    ):
        try:
            check()
            print(f"  {kind:8} {PREFIX}-{'connect' if kind == 'policy' else 'role'} exists")
        except iam.exceptions.NoSuchEntityException:
            print(f"  {kind:8} absent")
    groups = session.client("ec2").describe_security_groups(
        Filters=[{"Name": "group-name", "Values": [f"{PREFIX}-sg"]}]
    )["SecurityGroups"]
    print(f"  sg       {'exists (' + groups[0]['GroupId'] + ')' if groups else 'absent'}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", default=str(REPO_ROOT / ".env"), help="where the AWS credentials live")
    parser.add_argument("--region", help=f"AWS region (default: AWS_REGION or {DEFAULT_REGION})")
    sub = parser.add_subparsers(dest="command", required=True)

    up = sub.add_parser("up", help="create (or reuse) an IAM-enabled test instance")
    up.add_argument("engine", choices=list(ENGINES))
    up.add_argument("--yes", action="store_true", help="do not ask before creating the instance")
    up.set_defaults(func=cmd_up)

    down = sub.add_parser("down", help="delete the instance; shared resources go with the last one")
    down.add_argument("engine", choices=[*ENGINES, "all"])
    down.set_defaults(func=cmd_down)

    status = sub.add_parser("status", help="what exists right now")
    status.set_defaults(func=cmd_status)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
