import { useEffect, useState } from "react";
import { useFormContext } from "react-hook-form";
import { CheckCircleIcon, XCircleIcon } from "@heroicons/react/20/solid";
import {
  ConnectionPayload,
  TestConnectionResponse,
} from "../../../../api/DatasourceApi";
import { ApiResponse, isApiErrorResponse } from "../../../../api/Errors";
import Button from "../../../../components/Button";
import Spinner from "../../../../components/Spinner";
import { checkboxClassName } from "../../../../components/FormSection";
import {
  ConnectionFormInput,
  newConnectionFormSchema,
  newConnectionDefaults,
  toCreatePayload,
} from "./connectionForm";

// The test only depends on these, so it can run before the rest of the form is filled in.
const databaseFields = [
  "type",
  "protocol",
  "hostname",
  "port",
  "databaseName",
  "additionalJDBCOptions",
  "authenticationType",
  "username",
  "password",
  "roleArn",
] as const;

type TestResult =
  | { state: "idle" }
  | { state: "testing" }
  | { state: "done"; success: boolean; details: string; databases: string[] };

/**
 * Tests the database settings as entered. After a successful test that found several
 * databases it offers to create one connection per database, reported through
 * `onDatabasesChange` (null means a single connection).
 */
export default function TestConnection(props: {
  testConnection: (
    payload: ConnectionPayload,
  ) => Promise<ApiResponse<TestConnectionResponse>>;
  databases: string[] | null;
  onDatabasesChange: (databases: string[] | null) => void;
}) {
  const { getValues, trigger, watch } = useFormContext<ConnectionFormInput>();
  const [result, setResult] = useState<TestResult>({ state: "idle" });

  // A result describes the settings it was run with, so it goes stale when they change.
  const watched = watch(databaseFields);
  const fingerprint = JSON.stringify(watched);
  useEffect(() => {
    setResult({ state: "idle" });
    props.onDatabasesChange(null);
  }, [fingerprint]);

  const runTest = async () => {
    const values = getValues();
    const parsed = newConnectionFormSchema.safeParse({
      ...newConnectionDefaults(),
      ...Object.fromEntries(databaseFields.map((f) => [f, values[f]])),
      // The endpoint validates a whole connection; these are never stored.
      displayName: "Connection test",
      id: "connection-test",
    });
    if (!parsed.success) {
      await trigger(databaseFields);
      return;
    }
    setResult({ state: "testing" });
    const response = await props.testConnection(toCreatePayload(parsed.data));
    if (isApiErrorResponse(response)) {
      setResult({
        state: "done",
        success: false,
        details: response.message,
        databases: [],
      });
    } else {
      setResult({
        state: "done",
        success: response.success,
        details: response.details,
        databases: response.accessibleDatabases,
      });
    }
  };

  const toggleDatabase = (database: string) => {
    const selected = props.databases ?? [];
    props.onDatabasesChange(
      selected.includes(database)
        ? selected.filter((d) => d !== database)
        : [...selected, database],
    );
  };

  return (
    <div className="mt-1 border-t border-slate-200 pt-4 dark:border-slate-700">
      <div className="flex flex-wrap items-center gap-3">
        <Button
          onClick={() => void runTest()}
          variant={result.state === "testing" ? "disabled" : undefined}
          dataTestId="test-connection-button"
        >
          Test connection
        </Button>
        {result.state === "testing" && <Spinner size="sm" />}
        {result.state === "done" && result.success && (
          <span
            className="flex items-center gap-1 text-sm text-green-700 dark:text-green-400"
            data-testid="test-connection-success"
          >
            <CheckCircleIcon className="h-5 w-5" aria-hidden="true" />
            Connected
          </span>
        )}
        {result.state === "done" && !result.success && (
          <span
            className="flex items-center gap-1 text-sm text-red-600 dark:text-red-400"
            data-testid="test-connection-failure"
          >
            <XCircleIcon className="h-5 w-5" aria-hidden="true" />
            Connection failed
          </span>
        )}
      </div>
      {result.state === "done" && !result.success && result.details && (
        <p className="mt-2 break-words text-sm text-slate-600 dark:text-slate-300">
          {result.details}
        </p>
      )}
      {result.state === "done" &&
        result.success &&
        result.databases.length > 1 && (
          <div className="mt-3">
            <div className="flex items-start gap-3">
              <input
                id="create-per-database"
                type="checkbox"
                className={checkboxClassName}
                checked={props.databases !== null}
                onChange={(event) =>
                  props.onDatabasesChange(
                    event.target.checked ? result.databases : null,
                  )
                }
                data-testid="create-per-database"
              />
              <label htmlFor="create-per-database" className="text-sm">
                <span className="block font-medium text-slate-700 dark:text-slate-200">
                  Create one connection per database
                </span>
                <span className="block text-slate-500 dark:text-slate-400">
                  This user can reach {result.databases.length} databases. Each
                  selected database gets its own connection, named after this
                  one.
                </span>
              </label>
            </div>
            {props.databases !== null && (
              <ul className="ml-7 mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2">
                {result.databases.map((database) => (
                  <li key={database} className="flex items-center gap-2">
                    <input
                      id={`database-${database}`}
                      type="checkbox"
                      className={checkboxClassName.replace("mt-0.5 ", "")}
                      checked={props.databases?.includes(database) ?? false}
                      onChange={() => toggleDatabase(database)}
                    />
                    <label
                      htmlFor={`database-${database}`}
                      className="truncate font-mono text-sm text-slate-700 dark:text-slate-200"
                    >
                      {database}
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
    </div>
  );
}
