import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import CreateKubernetesConnectionForm from "./KubernetesConnectionForm";

const mockCreateConnection = vi.fn();

const renderForm = (initialCategory?: string) =>
  render(
    <CreateKubernetesConnectionForm
      handleCreateConnection={mockCreateConnection}
      initialCategory={initialCategory}
    />,
  );

describe("CreateKubernetesConnectionForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("submits the complete, typed connection payload", async () => {
    renderForm();

    userEvent.type(screen.getByTestId("kubernetes-connection-name"), "My Pods");
    userEvent.type(
      screen.getByTestId("kubernetes-connection-description"),
      "Prod cluster",
    );
    fireEvent.click(screen.getByTestId("create-kubernetes-connection-button"));

    await waitFor(() => expect(mockCreateConnection).toHaveBeenCalledTimes(1));
    expect(mockCreateConnection.mock.calls[0][0]).toEqual({
      connectionType: "KUBERNETES",
      displayName: "My Pods",
      id: "my-pods",
      description: "Prod cluster",
      reviewConfig: { numTotalRequired: 1 },
      maxExecutions: 1,
      storeResults: false,
      kubernetesExecInitialWaitTimeoutSeconds: 5,
      kubernetesExecTimeoutMinutes: 60,
    });
  });

  test("coerces typed numbers and keeps the initial category", async () => {
    renderForm("prod");

    userEvent.type(screen.getByTestId("kubernetes-connection-name"), "Pods");
    const reviews = screen.getByTestId(
      "kubernetes-connection-required-reviews",
    );
    userEvent.clear(reviews);
    userEvent.type(reviews, "3");
    fireEvent.click(screen.getByTestId("advanced-options-button"));
    const wait = screen.getByTestId(
      "kubernetes-exec-initial-wait-timeout-seconds",
    );
    userEvent.clear(wait);
    userEvent.type(wait, "10");
    const timeout = screen.getByTestId("kubernetes-exec-timeout-minutes");
    userEvent.clear(timeout);
    userEvent.type(timeout, "15");
    fireEvent.click(screen.getByTestId("create-kubernetes-connection-button"));

    await waitFor(() => expect(mockCreateConnection).toHaveBeenCalledTimes(1));
    expect(mockCreateConnection.mock.calls[0][0]).toMatchObject({
      category: "prod",
      reviewConfig: { numTotalRequired: 3 },
      kubernetesExecInitialWaitTimeoutSeconds: 10,
      kubernetesExecTimeoutMinutes: 15,
    });
  });
});
