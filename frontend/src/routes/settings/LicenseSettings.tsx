import { ChangeEvent, useEffect, useState } from "react";
import { ConfigResponse, getLicense, uploadLicense } from "../../api/ConfigApi";
import Spinner from "../../components/Spinner";
import { Warning } from "../../components/Alert";
import { ArrowUpCircleIcon, DocumentTextIcon } from "@heroicons/react/20/solid";
import Button from "../../components/Button";
import { useUsers } from "./UserSettings";

export default function GeneralSettings() {
  const { license, loading, refreshLicense } = useLicense();
  const { users } = useUsers();

  return (
    <div>
      <div className="max-w-7xl mx-auto">
        {loading ? (
          <Spinner />
        ) : (
          <div className="flex-col flex gap-y-2">
            {license && (
              <LicenseInfo
                license={license}
                userCount={users.length.toString()}
              />
            )}
            <LicenseStatus license={license!} />
            <LicenseDropZone refreshLicense={refreshLicense}></LicenseDropZone>
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
    { name: "License Valid until", stat: license.validUntil?.toDateString() },
    { name: "Users", stat: `${userCount}/${license.allowedUsers}` },
  ];

  return (
    <div>
      <dl className="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-2">
        {stats.map((item) => (
          <div
            key={item.name}
            className="overflow-hidden rounded-lg bg-white px-4 py-5 shadow sm:p-6 dark:bg-slate-900 dark:border dark:border-slate-800"
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
      <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative">
        <strong className="font-bold">License valid</strong>
        <span className="block sm:inline">
          {license.allowedUsers} users allowed
        </span>
      </div>
    );
  } else if (!license.licenseValid && license.validUntil === null) {
    return (
      <Warning>
        <span className="">
          You are currently running the{" "}
          <strong className="font-bold">free kviklet version</strong>. To buy a
          license contact us at{" "}
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

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files.length > 0) {
      setSelectedFile(event.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (selectedFile) {
      try {
        const response = await uploadLicense(selectedFile);
        await refreshLicense();
        setSelectedFile(null);
        console.log("Upload successful", response);
        // Handle further actions after successful upload
      } catch (error) {
        console.error("Error during file upload", error);
        // Handle errors
      }
    } else {
      console.error("No file selected");
      // Handle case when no file is selected
    }
  };

  return (
    <div className="flex flex-col">
      {(selectedFile && (
        <div className="flex flex-col items-center justify-center w-full bg-white shadow dark:shadow-none dark:bg-slate-900 dark:border-slate-800 dark:border rounded-lg">
          <div className="flex flex-col items-center justify-center pt-5 pb-6">
            <DocumentTextIcon className="h-12 w-12 text-slate-500 dark:text-slate-400" />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {selectedFile.name}
            </p>
          </div>
        </div>
      )) || (
        <div className="flex items-center justify-center w-full">
          <label
            htmlFor="dropzone-file"
            className="flex flex-col items-center justify-center w-full h-64 border-2 border-slate-300 dark:border-slate-700 border-dashed rounded-lg cursor-pointer bg-slate-50 dark:bg-slate-950 hover:bg-slate-100 dark:hover:border-slate-600 dark:hover:bg-slate-900"
          >
            <div className="flex flex-col items-center justify-center pt-5 pb-6">
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
      <p className="mt-2 ml-auto text-sm text-slate-500 dark:text-slate-400">
        {!selectedFile && "Upload a license file to activate it."}
      </p>
      <Button
        type={selectedFile ? "submit" : "disabled"}
        className="mt-2 ml-auto"
        onClick={() => {
          void handleUpload();
        }}
      >
        Upload
      </Button>
    </div>
  );
};

const useLicense = () => {
  const [license, setLicense] = useState<ConfigResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshLicense = async () => {
    setLoading(true);
    try {
      const license = await getLicense();
      setLicense(license);
      setLoading(false);
    } catch (err) {
      console.error(err);
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshLicense();
  }, []);

  return { license, loading, refreshLicense };
};
