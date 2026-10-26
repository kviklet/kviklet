import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import CreateKubernetesConnectionForm from "./KubernetesConnectionForm";

describe("CreateKubernetesConnectionForm", () => {
  test("submits the typed payload with coerced numbers", async () => {
    const mockCreateConnection = vi.fn();
    render(
      <CreateKubernetesConnectionForm
        handleCreateConnection={mockCreateConnection}
        initialCategory="prod"
      />,
    );

    userEvent.type(screen.getByTestId("kubernetes-connection-name"), "My Pods");
    userEvent.type(
      screen.getByTestId("kubernetes-connection-description"),
      "Prod cluster",
    );
    const reviews = screen.getByTestId(
      "kubernetes-connection-required-reviews",
    );
    userEvent.clear(reviews);
    userEvent.type(reviews, "3");
    fireEvent.click(screen.getByTestId("advanced-options-button"));
    const timeout = screen.getByTestId("kubernetes-exec-timeout-minutes");
    userEvent.clear(timeout);
    userEvent.type(timeout, "15");
    fireEvent.click(screen.getByTestId("create-kubernetes-connection-button"));

    await waitFor(() => expect(mockCreateConnection).toHaveBeenCalledTimes(1));
    expect(mockCreateConnection.mock.calls[0][0]).toEqual({
      connectionType: "KUBERNETES",
      displayName: "My Pods",
      id: "my-pods",
      description: "Prod cluster",
      category: "prod",
      reviewConfig: { numTotalRequired: 3 },
      maxExecutions: 1,
      storeResults: false,
      kubernetesExecInitialWaitTimeoutSeconds: 5,
      kubernetesExecTimeoutMinutes: 15,
    });
  });
});
