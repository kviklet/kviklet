import { ChangeEvent, useState } from "react";
import { ConfigResponse, uploadLicense } from "../../api/ConfigApi";
import Spinner from "../../components/Spinner";
import { Warning } from "../../components/Alert";
import { ArrowUpCircleIcon, DocumentTextIcon } from "@heroicons/react/20/solid";
import Button from "../../components/Button";
import { useUsers } from "./UserSettings";
import useConfig from "../../hooks/config";
import useNotification from "../../hooks/useNotification";

export default function LicenseSettings() {
  const { config, refreshConfig } = useConfig();
  const { users } = useUsers();

  return (
    <div>
      <div className="mx-auto max-w-7xl">
        {!config ? (
          <Spinner />
        ) : (
          <div className="flex flex-col gap-y-2">
            <LicenseInfo license={config} userCount={users.length.toString()} />
            <LicenseStatus license={config} />
            <LicenseDropZone refreshLicense={refreshConfig}></LicenseDropZone>
          </div>
        )}
      </div>
    </div>
  );
}

const LicenseInfo = ({
  license,
  userCount,
}: {
  license: ConfigResponse;
  userCount: string;
}) => {
  const stats = [
    {
      name: "License Valid until",
      stat: license.validUntil?.toDateString() || "-",
    },
    { name: "Users", stat: `${userCount}/${license.allowedUsers || "-"}` },
  ];

  return (
    <div>
      <dl className="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-2">
        {stats.map((item) => (
          <div
            key={item.name}
            className="overflow-hidden rounded-lg bg-white px-4 py-5 shadow dark:border dark:border-slate-800 dark:bg-slate-900 sm:p-6"
          >
            <dt className="truncate text-sm font-medium text-slate-500 dark:text-slate-400">
              {item.name}
            </dt>
            <dd className="mt-1 text-3xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
              {item.stat}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
};

const LicenseStatus = ({ license }: { license: ConfigResponse }) => {
  if (
    license.licenseValid &&
    license.validUntil &&
    license.validUntil > new Date()
  ) {
    return (
      <div className="relative rounded border border-green-400 bg-green-100 px-4 py-3 text-green-700">
        <strong className="font-bold">License valid </strong>
      </div>
    );
  } else if (!license.licenseValid && license.validUntil === null) {
    return (
      <Warning>
        <span className="">
          You are currently running the{" "}
          <strong className="font-bold">open-source version</strong> of Kviklet.
          To buy a license contact us at{" "}
          <a className="underline" href="https://kviklet.dev/">
            kviklet.dev
          </a>
          .
        </span>
      </Warning>
    );
  }
  return (
    <Warning>
      <span className="">
        Your license is <strong className="font-bold">expired</strong>. To renew
        your license contact us at{" "}
        <a className="underline" href="https://kviklet.dev/">
          kviklet.dev
        </a>
        . Until then you are limited to the features of our{" "}
        <strong className="font-bold">free</strong> version.
      </span>
    </Warning>
  );
};

const LicenseDropZone = ({
  refreshLicense,
}: {
  refreshLicense: () => Promise<void>;
}) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const { addNotification } = useNotification();

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files.length > 0) {
      setSelectedFile(event.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (selectedFile) {
      try {
        await uploadLicense(selectedFile);
        await refreshLicense();
        setSelectedFile(null);
        addNotification({
          title: "License uploaded successfully",
          text: "Your license has been activated.",
          type: "info",
        });
      } catch (error) {
        addNotification({
          title: "Failed to upload license",
          text:
            error instanceof Error
              ? error.message
              : "An unknown error occurred",
          type: "error",
        });
      }
    } else {
      addNotification({
        title: "No file selected",
        text: "Please select a license file to upload.",
        type: "error",
      });
    }
  };

  return (
    <div className="flex flex-col">
      {(selectedFile && (
        <div className="flex w-full flex-col items-center justify-center rounded-lg bg-white shadow dark:border dark:border-slate-800 dark:bg-slate-900 dark:shadow-none">
          <div className="flex flex-col items-center justify-center pb-6 pt-5">
            <DocumentTextIcon className="h-12 w-12 text-slate-500 dark:text-slate-400" />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {selectedFile.name}
            </p>
          </div>
        </div>
      )) || (
        <div className="flex w-full items-center justify-center">
          <label
            htmlFor="dropzone-file"
            className="flex h-64 w-full cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-950 dark:hover:border-slate-600 dark:hover:bg-slate-900"
          >
            <div className="flex flex-col items-center justify-center pb-6 pt-5">
              <ArrowUpCircleIcon className="h-12 w-12 text-slate-500 dark:text-slate-400" />
              <p className="mb-2 text-sm text-slate-500 dark:text-slate-400">
                <span className="font-semibold">Click to upload</span> or drag
                and drop
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                [license_name].json
              </p>
            </div>
            <input
              id="dropzone-file"
              type="file"
              className="hidden"
              onChange={handleFileChange}
            />
          </label>
        </div>
      )}
      <p className="ml-auto mt-2 text-sm text-slate-500 dark:text-slate-400">
        {!selectedFile && "Upload a license file to activate it."}
      </p>
      <Button
        type={selectedFile ? "submit" : "disabled"}
        className="ml-auto mt-2"
        onClick={() => {
          void handleUpload();
        }}
      >
        Upload
      </Button>
    </div>
  );
};
