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
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      id: "7wobWhQS1xwSX2JvoEAqHi",
      action: "role:get",
      effect: "ALLOW",
      resource: "*",
    },
    {
      id: "p61GMnqHX59yAcU6DpdBzA",
      action: "execution_request:edit",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      id: "p61GMnqHX59yAcU6DpdBzB",
      action: "execution_request:execute",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      id: "mK57KDGh1azk4RNcAiQFMu",
      action: "execution_request:get",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      id: "mK57KDGh1azk4RNcAiQFMv",
      action: "execution_request:review",
      effect: "ALLOW",
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
};

const expectedPayload = {
  id: "w66tvnQn2vHkyZ6xQBNLXj",
  name: "blabla",
  description: "blabla",
  policies: [
    {
      action: "role:get",
      effect: "ALLOW",
      resource: "*",
    },
    {
      action: "datasource_connection:get",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      action: "execution_request:get",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      action: "execution_request:review",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      action: "execution_request:edit",
      effect: "ALLOW",
      resource: "asdf",
    },
    {
      action: "execution_request:execute",
      effect: "ALLOW",
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
          effect: "ALLOW",
          resource: "*",
        },
      ],
    };

    const transformedRole = transformRole(adminRoleResponse);
    expect(transformedRole.isAdmin).toBe(true);
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
        effect: "ALLOW",
        resource: "*",
      },
    ]);
  });
});
