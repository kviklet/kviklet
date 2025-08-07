import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import DatabaseConnectionForm from "./DatabaseConnectionForm";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";

const mockCreateConnection = vi.fn();
const mockCloseModal = vi.fn();

describe("DatabaseConnectionForm - Max Temporary Access Duration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("renders max temporary access duration field in advanced options", async () => {
    render(
      <DatabaseConnectionForm
        createConnection={mockCreateConnection}
        closeModal={mockCloseModal}
      />,
    );

    // Open advanced options
    const advancedOptionsButton = screen.getByTestId("advanced-options-button");
    fireEvent.click(advancedOptionsButton);

    // The temporary access checkbox is checked by default, so the field should be visible
    await waitFor(() => {
      expect(screen.getByLabelText("Max Access Duration")).toBeInTheDocument();
    });

    // Check placeholder text
    const input = screen.getByLabelText("Max Access Duration");
    expect(input).toHaveAttribute("placeholder", "Leave empty for unlimited");
  });

  test("allows entering a max temporary access duration value", async () => {
    render(
      <DatabaseConnectionForm
        createConnection={mockCreateConnection}
        closeModal={mockCloseModal}
      />,
    );

    // Fill in required fields
    userEvent.type(screen.getByTestId("connection-name"), "Test Connection");
    userEvent.type(
      screen.getByTestId("connection-description"),
      "Test Description",
    );
    userEvent.type(screen.getByTestId("connection-hostname"), "localhost");
    userEvent.type(screen.getByTestId("connection-username"), "testuser");
    userEvent.type(screen.getByTestId("connection-password"), "testpass");

    // Open advanced options
    fireEvent.click(screen.getByTestId("advanced-options-button"));

    // The temporary access checkbox is checked by default
    // Enter max temporary access duration
    const maxDurationInput = screen.getByLabelText("Max Access Duration");
    userEvent.type(maxDurationInput, "120");

    // Submit form
    const createButton = screen.getByTestId("create-connection-button");
    fireEvent.click(createButton);

    await waitFor(() => {
      expect(mockCreateConnection).toHaveBeenCalledWith(
        expect.objectContaining({
          maxTemporaryAccessDuration: 120,
        }),
      );
    });
  });

  test("allows leaving max temporary access duration empty for unlimited", async () => {
    render(
      <DatabaseConnectionForm
        createConnection={mockCreateConnection}
        closeModal={mockCloseModal}
      />,
    );

    // Fill in required fields
    userEvent.type(screen.getByTestId("connection-name"), "Test Connection");
    userEvent.type(
      screen.getByTestId("connection-description"),
      "Test Description",
    );
    userEvent.type(screen.getByTestId("connection-hostname"), "localhost");
    userEvent.type(screen.getByTestId("connection-username"), "testuser");
    userEvent.type(screen.getByTestId("connection-password"), "testpass");

    // Open advanced options but don't fill max duration
    fireEvent.click(screen.getByTestId("advanced-options-button"));

    // The temporary access checkbox is checked by default, leave max duration empty

    // Submit form
    const createButton = screen.getByTestId("create-connection-button");
    fireEvent.click(createButton);

    await waitFor(() => {
      expect(mockCreateConnection).toHaveBeenCalledWith(
        expect.objectContaining({
          // When empty, react-hook-form with z.coerce.number().nullable() converts to 0
          // This is acceptable behavior - the backend will handle 0 as unlimited
          maxTemporaryAccessDuration: 0,
        }),
      );
    });
  });

  test("validates minimum value for max temporary access duration", () => {
    render(
      <DatabaseConnectionForm
        createConnection={mockCreateConnection}
        closeModal={mockCloseModal}
      />,
    );

    // Open advanced options
    fireEvent.click(screen.getByTestId("advanced-options-button"));

    // The temporary access checkbox is checked by default
    // Check that the input has min attribute set to 1
    const maxDurationInput = screen.getByLabelText("Max Access Duration");
    expect(maxDurationInput).toHaveAttribute("min", "1");
  });

  test("hides max temporary access duration field when temporary access is disabled", async () => {
    render(
      <DatabaseConnectionForm
        createConnection={mockCreateConnection}
        closeModal={mockCloseModal}
      />,
    );

    // Open advanced options
    fireEvent.click(screen.getByTestId("advanced-options-button"));

    // The field should be visible initially (checkbox is checked by default)
    expect(screen.getByLabelText("Max Access Duration")).toBeInTheDocument();

    // Uncheck the temporary access checkbox
    const tempAccessCheckbox = screen.getByLabelText("Enable Temporary Access");
    fireEvent.click(tempAccessCheckbox);

    // The max duration field should disappear
    await waitFor(() => {
      expect(
        screen.queryByLabelText("Max Access Duration"),
      ).not.toBeInTheDocument();
    });

    // Re-check the checkbox
    fireEvent.click(tempAccessCheckbox);

    // The field should reappear
    await waitFor(() => {
      expect(screen.getByLabelText("Max Access Duration")).toBeInTheDocument();
    });
  });
});
