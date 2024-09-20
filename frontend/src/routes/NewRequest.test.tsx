import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import NewRequest from "./NewRequest";

describe("NewRequest Component", () => {
  test("renders duration input for TemporaryAccess", () => {
    render(
      <MemoryRouter>
        <NewRequest />
      </MemoryRouter>
    );

    // Simulate selecting a connection and mode
    fireEvent.click(screen.getByText("Connections"));
    fireEvent.click(screen.getByText("Query"));
    fireEvent.click(screen.getByText("Access"));

    // Check if the duration input is rendered
    const durationInput = screen.getByTestId("request-duration");
    expect(durationInput).toBeInTheDocument();
  });

  test("submits form with duration", () => {
    render(
      <MemoryRouter>
        <NewRequest />
      </MemoryRouter>
    );

    // Simulate selecting a connection and mode
    fireEvent.click(screen.getByText("Connections"));
    fireEvent.click(screen.getByText("Query"));
    fireEvent.click(screen.getByText("Access"));

    // Fill out the form
    fireEvent.change(screen.getByTestId("request-title"), {
      target: { value: "Test Title" },
    });
    fireEvent.change(screen.getByTestId("request-description"), {
      target: { value: "Test Description" },
    });
    fireEvent.change(screen.getByTestId("request-duration"), {
      target: { value: "3600" },
    });

    // Submit the form
    fireEvent.click(screen.getByTestId("submit-button"));

    // Check if the form was submitted with the correct data
    // This part depends on how the form submission is handled in your code
    // You might need to mock the API call and check if it was called with the correct data
  });
});
