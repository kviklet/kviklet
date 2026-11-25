import { useFormContext } from "react-hook-form";
import {
  CheckboxRow,
  FormSection,
  checkboxClassName,
} from "../../../../components/FormSection";
import {
  ConnectionFormInput,
  supportsDryRun,
  supportsDumps,
} from "./connectionForm";

type BooleanField =
  | "temporaryAccessEnabled"
  | "dryRunEnabled"
  | "dryRunRequiresApproval"
  | "explainEnabled"
  | "dumpsEnabled"
  | "storeResults";

// Controlled instead of register(): which options exist depends on the connection type, and
// react-hook-form resets a registered checkbox to unchecked when it unmounts and mounts again.
function Option(props: {
  name: BooleanField;
  label: string;
  description: string;
  testId: string;
}) {
  const { watch, setValue } = useFormContext<ConnectionFormInput>();
  return (
    <CheckboxRow
      id={props.name}
      label={props.label}
      description={props.description}
    >
      <input
        id={props.name}
        type="checkbox"
        className={checkboxClassName}
        data-testid={props.testId}
        checked={watch(props.name)}
        onChange={(event) =>
          setValue(props.name, event.target.checked, { shouldDirty: true })
        }
      />
    </CheckboxRow>
  );
}

export default function RequestOptionsSection() {
  const { watch } = useFormContext<ConnectionFormInput>();
  const isDatabase = watch("connectionType") === "DATASOURCE";
  const type = watch("type");

  if (!isDatabase) {
    return (
      <FormSection title="Request options">
        <Option
          name="storeResults"
          label="Store command output"
          description="Keeps up to 50 KB of each command's output in the request history."
          testId="connection-store-results"
        />
      </FormSection>
    );
  }

  return (
    <FormSection title="Request options">
      <Option
        name="temporaryAccessEnabled"
        label="Temporary access"
        description="Time-limited sessions where every statement is logged."
        testId="connection-temporary-access"
      />
      {supportsDryRun(type) && (
        <Option
          name="dryRunEnabled"
          label="Dry run"
          description="Runs a request without committing its changes. This relies on SQL parsing, which is not fully reliable."
          testId="connection-dry-run"
        />
      )}
      {supportsDryRun(type) && watch("dryRunEnabled") && (
        <div className="ml-7">
          <Option
            name="dryRunRequiresApproval"
            label="Dry runs need approval"
            description="When off, a request can be dry-run before it has been approved."
            testId="connection-dry-run-requires-approval"
          />
        </div>
      )}
      <Option
        name="explainEnabled"
        label="Explain"
        description="Shows the query plan on a request. This relies on SQL parsing, so it is best kept to read-only connections."
        testId="connection-explain"
      />
      {supportsDumps(type) && (
        <Option
          name="dumpsEnabled"
          label="SQL dump"
          description="Allows requests that export the whole database."
          testId="connection-dumps"
        />
      )}
      <Option
        name="storeResults"
        label="Store query results"
        description="Keeps the first 500 rows of each result in the request history."
        testId="connection-store-results"
      />
    </FormSection>
  );
}
