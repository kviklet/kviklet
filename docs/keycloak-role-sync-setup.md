# Keycloak Role Sync Setup

This guide explains how to configure Keycloak to send group memberships in OIDC tokens for Kviklet's role sync feature.

## Prerequisites

- Keycloak instance running
- A realm and client configured for Kviklet
- Users and groups created in Keycloak

## Step 1: Create Groups

1. Go to your Keycloak Admin Console
2. Select your realm
3. Navigate to **Groups** in the left menu
4. Click **Create group**
5. Enter a group name (e.g., `developers`, `admins`)
6. Click **Create**

## Step 2: Add Users to Groups

1. Navigate to **Users** in the left menu
2. Select a user
3. Go to the **Groups** tab
4. Click **Join Group**
5. Select the group(s) to add the user to

## Step 3: Configure Group Membership Mapper

This is the critical step - Keycloak doesn't include groups in tokens by default.

1. Navigate to **Clients** in the left menu
2. Select your Kviklet client
3. Go to the **Client scopes** tab
4. Click on the dedicated scope (e.g., `kviklet-dedicated`)
5. Go to the **Mappers** tab
6. Click **Add mapper** → **By configuration**
7. Select **Group Membership**
8. Configure the mapper:

| Setting | Value |
|---------|-------|
| Name | `groups` |
| Token Claim Name | `groups` |
| Full group path | **OFF** |
| Add to ID token | **ON** |
| Add to access token | **ON** |
| Add to userinfo | **ON** |

9. Click **Save**

> **Important:** The "Token Claim Name" must match the "Groups Attribute" configured in Kviklet's Role Sync settings (default: `groups`).

## Step 4: Configure Kviklet

1. Go to **Settings** → **Role Sync** (requires enterprise license)
2. Enable role sync
3. Set **Groups Attribute** to `groups` (must match Token Claim Name from Step 3)
4. Select a **Sync Mode**:
   - **Full Sync**: Roles exactly match IdP group mappings
   - **Additive**: IdP groups add roles without removing existing ones
   - **First Login Only**: Roles only set on first login
5. Add mappings between Keycloak group names and Kviklet roles

## Verification

1. Log in as a user who belongs to a mapped group
2. Check that the user has the expected Kviklet role(s)
3. The default role should always be present

## Troubleshooting

If roles aren't syncing, check the backend logs for:

```
Looking for groups attribute 'groups' in attributes: [...]
```

- If `groups` is not in the attributes list, the Keycloak mapper isn't configured correctly
- Ensure "Add to ID token" is **ON** in the mapper configuration
- Ensure "Full group path" is **OFF** (otherwise groups appear as `/groupname` instead of `groupname`)
