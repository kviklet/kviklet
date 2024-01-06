import { render, fireEvent, screen } from "@testing-library/react";
import ProfileSettings from "./ProfileSettings";
import { UserStatusContext } from "../../components/UserStatusProvider";
import { updateUser } from "../../api/UserApi";
import { vi } from "vitest";

// Mock the API
vi.mock("../../api/UserApi");

// Utility function to render the component with mock context
const mockUserStatus = {
  userStatus: {
    email: "test@email.com",
    fullName: "test name",
    status: "logged in",
    id: "123456",
  },
  refreshState: vi.fn().mockResolvedValue(undefined),
};

const renderProfileSettings = (userStatus) => {
  return render(
    <UserStatusContext.Provider value={mockUserStatus}>
      <ProfileSettings />
    </UserStatusContext.Provider>,
  );
};

describe("ProfileSettings", () => {
  it("renders without crashing", () => {
    renderProfileSettings({ userStatus: { id: "123" } });
    // Assert that key elements are present
  });

  it("shows an alert when passwords do not match", () => {
    renderProfileSettings({ userStatus: { id: "123" } });
    // Simulate entering different passwords and assert that the alert is shown
  });

  it("shows a success message on successful password change", async () => {
    updateUser.mockResolvedValue({ id: "123" }); // Mocking successful API call
    renderProfileSettings({ userStatus: { id: "123" } });

    // Simulate entering matching passwords and clicking save
    // Assert that the success banner is shown
  });

  // Additional tests...
});
