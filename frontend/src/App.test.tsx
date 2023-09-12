import React from "react";
import { render, screen } from "@testing-library/react";
import App from "./App";
import { vi } from "vitest";

test("renders learn react link", () => {
  render(<App />);
  const linkElement = screen.getByText(/Opsgate/i);
  expect(linkElement).toBeInTheDocument();
});
