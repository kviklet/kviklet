import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import MultiResult from "./MultiResult";
import { UpdateExecuteResponse } from "../api/ExecutionRequestApi";

const update: UpdateExecuteResponse = { _type: "update", rowsUpdated: 0 };

describe("MultiResult", () => {
  it("shows the warnings the database sent with the result", () => {
    render(
      <MultiResult
        results={[update]}
        warnings={[
          'skipping "booking" --- only table or database owner can analyze it',
        ]}
      />,
    );
    expect(screen.getByTestId("result-warnings")).toHaveTextContent(
      'skipping "booking" --- only table or database owner can analyze it',
    );
  });

  it("shows no warning block without warnings", () => {
    render(<MultiResult results={[update]} warnings={[]} />);
    expect(screen.queryByTestId("result-warnings")).toBeNull();
  });
});
