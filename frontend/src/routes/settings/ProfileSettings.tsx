import { ChangeEvent, useContext, useState } from "react";
import Button from "../../components/Button";
import { Error, Success } from "../../components/Alert";
import { updateUser } from "../../api/UserApi";
import { UserStatusContext } from "../../components/UserStatusProvider";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import { useHasPermission } from "../../hooks/permissions";
import ReadOnlyNotice from "../../components/ReadOnlyNotice";

function ProfileSettings() {
  const userContext = useContext(UserStatusContext);
  // Changing the own password goes through PATCH /users/{id}, which requires user:edit.
  const canEditSelf = useHasPermission("user:edit");
  const userStatus = userContext.userStatus || undefined;

  return (
    <div>
      <h2 className="mb-4 text-lg text-slate-900 dark:text-slate-50">
        Profile
      </h2>
      <div className="mb-6 rounded-md border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
        <dl className="space-y-3">
          <div>
            <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Name
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-50">
              {userStatus?.fullName || "—"}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">
              Email
            </dt>
            <dd className="text-sm text-slate-900 dark:text-slate-50">
              {userStatus?.email || "—"}
            </dd>
          </div>
        </dl>
      </div>
      {canEditSelf ? (
        <ChangePasswordForm />
      ) : (
        <ReadOnlyNotice>
          Profile editing is not enabled for your role. Ask an administrator if
          you need to change your password.
        </ReadOnlyNotice>
      )}
    </div>
  );
}

function ChangePasswordForm() {
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmNewPassowrd, setConfirmNewPassowrd] = useState<string>("");

  const [showSuccessBanner, setShowSuccessBanner] = useState<boolean>(false);
  const userContext = useContext(UserStatusContext);

  const { addNotification } = useNotification();

  const changePassword = async () => {
    if (newPassword === confirmNewPassowrd && userContext.userStatus) {
      const response = await updateUser(userContext.userStatus.id, {
        password: confirmNewPassowrd,
      });
      if (isApiErrorResponse(response)) {
        addNotification({
          title: "Failed to change password",
          text: response.message,
          type: "error",
        });
      } else {
        setShowSuccessBanner(true);
      }
    }
  };

  return (
    <div>
      <div>
        <div>Change Password</div>
        <label
          htmlFor="new-password"
          className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
        >
          New password
        </label>
        <div className="relative mt-2 flex items-center">
          <input
            type="password"
            name="new-password"
            id="new-password"
            className="my-3 block w-full rounded-md border-0 px-3 py-1.5 pb-1.5 pr-14 pt-2.5 text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus-visible:outline-none dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 sm:text-sm sm:leading-6"
            autoComplete={undefined}
            value={newPassword}
            onChange={(e: ChangeEvent<HTMLInputElement>) =>
              setNewPassword(e.target.value)
            }
          />
        </div>
        <label
          htmlFor="new-password-confirm"
          className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
        >
          New password confirm
        </label>
        <input
          type="password"
          name="new-password-confirm"
          id="new-password-confirm"
          className="my-3 block w-full rounded-md border-0 px-3 py-1.5 pb-1.5 pr-14 pt-2.5 text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus-visible:outline-none dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 sm:text-sm sm:leading-6"
          autoComplete={undefined}
          value={confirmNewPassowrd}
          onChange={(e: ChangeEvent<HTMLInputElement>) =>
            setConfirmNewPassowrd(e.target.value)
          }
        />
      </div>
      <div className="mb-2 flex justify-end">
        <Button onClick={() => void changePassword()}>Save</Button>
      </div>
      {newPassword !== confirmNewPassowrd && (
        <Error>
          <h3>Can't change password</h3>
          <p>Passwords don't match</p>
        </Error>
      )}
      {showSuccessBanner && <Success>Successfully changed password!</Success>}
    </div>
  );
}
export default ProfileSettings;
