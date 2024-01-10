import { useContext, useState } from "react";
import Button from "../../components/Button";
import Error from "../../components/Alert";
import { updateUser } from "../../api/UserApi";
import { UserStatusContext } from "../../components/UserStatusProvider";
import Success from "../../components/Success";

function ProfileSettings() {
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmNewPassowrd, setConfirmNewPassowrd] = useState<string>("");

  const [showSuccessBanner, setShowSuccessBanner] = useState<boolean>(false);
  const userContext = useContext(UserStatusContext);

  const changePassword = async () => {
    if (newPassword === confirmNewPassowrd && userContext.userStatus) {
      const newUser = await updateUser(userContext.userStatus.id, {
        password: confirmNewPassowrd,
      });
      if (newUser.id) {
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
            className="block dark:bg-slate-900 w-full rounded-md border-0 py-1.5 pr-14 text-slate-900 dark:text-slate-50 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset sm:text-sm sm:leading-6 px-3 my-3 pb-1.5 pt-2.5 dark:ring-slate-700 focus-visible:outline-none"
            autoComplete={undefined}
            value={newPassword}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
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
          className="block dark:bg-slate-900 w-full rounded-md border-0 py-1.5 pr-14 text-slate-900 dark:text-slate-50 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset sm:text-sm sm:leading-6 px-3 my-3 pb-1.5 pt-2.5 dark:ring-slate-700 focus-visible:outline-none"
          autoComplete={undefined}
          value={confirmNewPassowrd}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setConfirmNewPassowrd(e.target.value)
          }
        />
      </div>
      <div className="flex justify-end mb-2">
        <Button onClick={() => void changePassword()}>Save</Button>
      </div>
      {newPassword !== confirmNewPassowrd && (
        <Error heading="Can't change password">
          <li>Passwords don't match</li>
        </Error>
      )}
      {showSuccessBanner && (
        <Success heading="Successfully changed password!" text=""></Success>
      )}
    </div>
  );
}
export default ProfileSettings;
