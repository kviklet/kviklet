import { describe, it, expect } from "vitest";
import { transformRole, transformToPayload } from "./roles";

const mockRoleResponse = {
  id: "w66tvnQn2vHkyZ6xQBNLXj",
  name: "blabla",
  description: "blabla",
  policies: [
    {
      id: "9ssS83qFmzQTYWraAhV9Hh",
      action: "datasource_connection:get",
      resource: "asdf",
    },
    {
      id: "7wobWhQS1xwSX2JvoEAqHi",
      action: "role:get",
      resource: "*",
    },
    {
      id: "p61GMnqHX59yAcU6DpdBzA",
      action: "execution_request:edit",
      resource: "asdf",
    },
    {
      id: "p61GMnqHX59yAcU6DpdBzB",
      action: "execution_request:execute",
      resource: "asdf",
    },
    {
      id: "mK57KDGh1azk4RNcAiQFMu",
      action: "execution_request:get",
      resource: "asdf",
    },
    {
      id: "mK57KDGh1azk4RNcAiQFMv",
      action: "execution_request:review",
      resource: "asdf",
    },
  ],
  isDefault: false,
};

const expectedTransformedRole = {
  id: "w66tvnQn2vHkyZ6xQBNLXj",
  name: "blabla",
  description: "blabla",
  isAdmin: false,
  userPolicy: {
    read: false,
    create: false,
    editSelf: false,
  },
  rolePolicy: {
    read: true,
  },
  connectionPolicies: [
    {
      selector: "asdf",
      execution_request_read: true,
      execution_request_write: true,
      execution_request_review: true,
    },
  ],
  otherPolicies: [],
};

const mockRole = {
  id: "w66tvnQn2vHkyZ6xQBNLXj",
  name: "blabla",
  description: "blabla",
  isAdmin: false,
  userPolicy: {
    read: false,
    create: false,
    editSelf: false,
  },
  rolePolicy: {
    read: true,
  },
  connectionPolicies: [
    {
      selector: "asdf",
      execution_request_read: true,
      execution_request_write: true,
      execution_request_review: true,
    },
  ],
  otherPolicies: [],
};

const expectedPayload = {
  id: "w66tvnQn2vHkyZ6xQBNLXj",
  name: "blabla",
  description: "blabla",
  policies: [
    {
      action: "role:get",
      resource: "*",
    },
    {
      action: "datasource_connection:get",
      resource: "asdf",
    },
    {
      action: "execution_request:get",
      resource: "asdf",
    },
    {
      action: "execution_request:review",
      resource: "asdf",
    },
    {
      action: "execution_request:edit",
      resource: "asdf",
    },
    {
      action: "execution_request:execute",
      resource: "asdf",
    },
  ],
};

describe("transformRole", () => {
  it("should transform RoleResponse to Role correctly", () => {
    const transformedRole = transformRole(mockRoleResponse);
    expect(transformedRole).toEqual(expectedTransformedRole);
  });

  it("should set isAdmin to true if the role has admin privileges", () => {
    const adminRoleResponse = {
      ...mockRoleResponse,
      policies: [
        ...mockRoleResponse.policies,
        {
          id: "adminPolicy",
          action: "*:*",
          resource: "*",
        },
      ],
    };

    const transformedRole = transformRole(adminRoleResponse);
    expect(transformedRole.isAdmin).toBe(true);
  });
});

describe("policies the form has no control for", () => {
  const apiRole = {
    id: "role-1",
    name: "Operators",
    description: null,
    isDefault: false,
    policies: [
      { id: "1", action: "configuration:get", resource: "*" },
      { id: "2", action: "datasource_connection:get", resource: "pg-*" },
      { id: "3", action: "datasource_connection:edit", resource: "pg-*" },
      { id: "4", action: "execution_request:get", resource: "pg-*" },
      // Execute without edit is not what the write flag means.
      { id: "5", action: "execution_request:execute", resource: "pg-*" },
      // A user permission scoped to one resource is not the global checkbox.
      { id: "6", action: "user:get", resource: "some-user" },
    ],
  };

  it("keeps them apart instead of folding them into the form fields", () => {
    const role = transformRole(apiRole);
    expect(role.userPolicy.read).toBe(false);
    expect(role.connectionPolicies).toEqual([
      {
        selector: "pg-*",
        execution_request_read: true,
        execution_request_write: false,
        execution_request_review: false,
      },
    ]);
    expect(role.otherPolicies).toEqual([
      { action: "configuration:get", resource: "*" },
      { action: "datasource_connection:edit", resource: "pg-*" },
      { action: "execution_request:execute", resource: "pg-*" },
      { action: "user:get", resource: "some-user" },
    ]);
  });

  it("sends them back unchanged, so saving a role loses nothing", () => {
    const payload = transformToPayload(transformRole(apiRole));
    const sent = payload.policies.map((p) => `${p.action} ${p.resource}`);
    expect(sent.sort()).toEqual(
      apiRole.policies.map((p) => `${p.action} ${p.resource}`).sort(),
    );
  });

  it("keeps them when the role is made an admin", () => {
    const payload = transformToPayload({
      ...transformRole(apiRole),
      isAdmin: true,
    });
    expect(payload.policies).toContainEqual({ action: "*:*", resource: "*" });
    expect(payload.policies).toContainEqual({
      action: "configuration:get",
      resource: "*",
    });
  });
});

describe("transformToPayload", () => {
  it("should transform Role to RoleUpdatePayload correctly", () => {
    const payload = transformToPayload(mockRole);
    expect(payload).toEqual(expectedPayload);
  });

  it("should handle isAdmin role correctly in payload transformation", () => {
    const adminRole = {
      ...mockRole,
      isAdmin: true,
    };

    const adminPayload = transformToPayload(adminRole);
    expect(adminPayload.policies).toEqual([
      {
        action: "*:*",
        resource: "*",
      },
    ]);
  });
});
