import { renderHook, act } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, MockedFunction } from "vitest";
import useConnections from "./connections";
import {
  ConnectionPayload,
  DatabaseConnectionResponse,
  TestConnectionResponse,
  DatabaseType,
  DatabaseProtocol,
  AuthenticationType,
  getConnections,
  addConnection,
  testConnection,
} from "../api/DatasourceApi";

// Mock the API module
vi.mock("../api/DatasourceApi");

// Mock the notification hook
vi.mock("./useNotification", () => ({
  default: () => ({
    addNotification: vi.fn(),
  }),
}));

// Get mocked functions with proper typing
const mockGetConnections = getConnections as MockedFunction<
  typeof getConnections
>;
const mockAddConnection = addConnection as MockedFunction<typeof addConnection>;
const mockTestConnection = testConnection as MockedFunction<
  typeof testConnection
>;

describe("useConnections hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetConnections.mockResolvedValue([]);
  });

  describe("testConnection", () => {
    it("should normalize maxTemporaryAccessDuration when value is 0", async () => {
      const testResponse: TestConnectionResponse = {
        success: true,
        details: "Connection successful",
        accessibleDatabases: ["db1", "db2"],
      };
      mockTestConnection.mockResolvedValue(testResponse);

      const { result } = renderHook(() => useConnections());

      const connectionPayload: ConnectionPayload = {
        connectionType: "DATASOURCE",
        id: "test-conn",
        displayName: "Test Connection",
        databaseName: "test_db",
        maxTemporaryAccessDuration: 0,
        username: "user",
        password: "pass",
        type: DatabaseType.POSTGRES,
        protocol: DatabaseProtocol.POSTGRESQL,
        hostname: "localhost",
        port: 5432,
        reviewConfig: { numTotalRequired: 1 },
        authenticationType: AuthenticationType.USER_PASSWORD,
        description: "",
        additionalJDBCOptions: "",
        dumpsEnabled: false,
        temporaryAccessEnabled: true,
        explainEnabled: false,
        maxExecutions: null,
      };

      await act(async () => {
        await result.current.testConnection(connectionPayload);
      });

      expect(mockTestConnection).toHaveBeenCalledWith({
        ...connectionPayload,
        maxTemporaryAccessDuration: null,
      });
    });

    it("should normalize maxTemporaryAccessDuration when value is undefined", async () => {
      const testResponse: TestConnectionResponse = {
        success: true,
        details: "Connection successful",
        accessibleDatabases: ["db1"],
      };
      mockTestConnection.mockResolvedValue(testResponse);

      const { result } = renderHook(() => useConnections());

      const connectionPayload: ConnectionPayload = {
        connectionType: "DATASOURCE",
        id: "test-conn",
        displayName: "Test Connection",
        databaseName: "test_db",
        maxTemporaryAccessDuration: undefined,
        username: "user",
        password: "pass",
        type: DatabaseType.POSTGRES,
        protocol: DatabaseProtocol.POSTGRESQL,
        hostname: "localhost",
        port: 5432,
        reviewConfig: { numTotalRequired: 1 },
        authenticationType: AuthenticationType.USER_PASSWORD,
        description: "",
        additionalJDBCOptions: "",
        dumpsEnabled: false,
        temporaryAccessEnabled: true,
        explainEnabled: false,
        maxExecutions: null,
      };

      await act(async () => {
        await result.current.testConnection(connectionPayload);
      });

      expect(mockTestConnection).toHaveBeenCalledWith({
        ...connectionPayload,
        maxTemporaryAccessDuration: null,
      });
    });

    it("should preserve maxTemporaryAccessDuration when value is positive", async () => {
      const testResponse: TestConnectionResponse = {
        success: true,
        details: "Connection successful",
        accessibleDatabases: ["db1"],
      };
      mockTestConnection.mockResolvedValue(testResponse);

      const { result } = renderHook(() => useConnections());

      const connectionPayload: ConnectionPayload = {
        connectionType: "DATASOURCE",
        id: "test-conn",
        displayName: "Test Connection",
        databaseName: "test_db",
        maxTemporaryAccessDuration: 120,
        username: "user",
        password: "pass",
        type: DatabaseType.POSTGRES,
        protocol: DatabaseProtocol.POSTGRESQL,
        hostname: "localhost",
        port: 5432,
        reviewConfig: { numTotalRequired: 1 },
        authenticationType: AuthenticationType.USER_PASSWORD,
        description: "",
        additionalJDBCOptions: "",
        dumpsEnabled: false,
        temporaryAccessEnabled: true,
        explainEnabled: false,
        maxExecutions: null,
      };

      await act(async () => {
        await result.current.testConnection(connectionPayload);
      });

      expect(mockTestConnection).toHaveBeenCalledWith({
        ...connectionPayload,
        maxTemporaryAccessDuration: 120,
      });
    });

    it("should not modify Kubernetes connections", async () => {
      const testResponse: TestConnectionResponse = {
        success: true,
        details: "Connection successful",
        accessibleDatabases: [],
      };
      mockTestConnection.mockResolvedValue(testResponse);

      const { result } = renderHook(() => useConnections());

      const connectionPayload: ConnectionPayload = {
        connectionType: "KUBERNETES",
        id: "k8s-conn",
        displayName: "K8s Connection",
        description: "",
        reviewConfig: { numTotalRequired: 1 },
        maxExecutions: null,
      };

      await act(async () => {
        await result.current.testConnection(connectionPayload);
      });

      expect(mockTestConnection).toHaveBeenCalledWith(connectionPayload);
    });
  });

  describe("createConnection", () => {
    it("should normalize maxTemporaryAccessDuration when creating datasource connection", async () => {
      const connectionResponse: DatabaseConnectionResponse = {
        id: "test-conn",
        displayName: "Test Connection",
        type: DatabaseType.POSTGRES,
        protocol: DatabaseProtocol.POSTGRESQL,
        maxExecutions: null,
        authenticationType: AuthenticationType.USER_PASSWORD,
        databaseName: "test_db",
        username: "user",
        hostname: "localhost",
        port: 5432,
        description: "",
        reviewConfig: { numTotalRequired: 1 },
        additionalJDBCOptions: "",
        dumpsEnabled: false,
        temporaryAccessEnabled: true,
        explainEnabled: false,
        roleArn: null,
        maxTemporaryAccessDuration: null,
        _type: "DATASOURCE",
      };
      mockAddConnection.mockResolvedValue(connectionResponse);

      const { result } = renderHook(() => useConnections());

      const connectionPayload: ConnectionPayload = {
        connectionType: "DATASOURCE",
        id: "test-conn",
        displayName: "Test Connection",
        databaseName: "test_db",
        maxTemporaryAccessDuration: 0,
        username: "user",
        password: "pass",
        type: DatabaseType.POSTGRES,
        protocol: DatabaseProtocol.POSTGRESQL,
        hostname: "localhost",
        port: 5432,
        reviewConfig: { numTotalRequired: 1 },
        authenticationType: AuthenticationType.USER_PASSWORD,
        description: "",
        additionalJDBCOptions: "",
        dumpsEnabled: false,
        temporaryAccessEnabled: true,
        explainEnabled: false,
        maxExecutions: null,
      };

      await act(async () => {
        await result.current.createConnection(connectionPayload);
      });

      expect(mockAddConnection).toHaveBeenCalledWith({
        ...connectionPayload,
        maxTemporaryAccessDuration: null,
      });
    });
  });
});
