import React, { useState, useEffect, FormEvent } from "react";
import {
  ApiKeyResponse,
  ApiKeyWithSecretResponse,
  listApiKeys,
  createApiKey,
  deleteApiKey,
} from "../../api/ApiKeyApi";
import { Error, Success } from "../../components/Alert";
import { format } from "date-fns";
import {
  PlusIcon,
  TrashIcon,
  ClipboardIcon,
  CheckIcon,
  ExclamationTriangleIcon,
} from "@heroicons/react/24/outline";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";

export default function ApiKeyPage() {
  const [apiKeys, setApiKeys] = useState<ApiKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newKeyName, setNewKeyName] = useState("");
  const [expiresInDays, setExpiresInDays] = useState<number | null>(30);
  const [newApiKey, setNewApiKey] = useState<ApiKeyWithSecretResponse | null>(
    null,
  );
  const [copied, setCopied] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  const { addNotification } = useNotification();

  useEffect(() => {
    void loadApiKeys();
  }, []);

  const loadApiKeys = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await listApiKeys();
      if (isApiErrorResponse(response)) {
        addNotification({
          title: "Failed to load API keys",
          text: response.message,
          type: "error",
        });
      } else {
        setApiKeys(response.apiKeys);
      }
    } catch (err) {
      setError("Failed to load API keys");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateKey = async (e: FormEvent) => {
    e.preventDefault();
    if (!newKeyName.trim()) {
      setError("API key name is required");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const createdKey = await createApiKey({
        name: newKeyName.trim(),
        expiresInDays: expiresInDays,
      });
      if (isApiErrorResponse(createdKey)) {
        addNotification({
          title: "Failed to create API key",
          text: createdKey.message,
          type: "error",
        });
      } else {
        setNewApiKey(createdKey);
        setShowCreateForm(false);
        setNewKeyName("");
        setSuccess("API key created successfully");
        await loadApiKeys();
      }
    } catch (err) {
      setError("Failed to create API key");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteKey = async (id: string) => {
    try {
      setLoading(true);
      setError(null);
      await deleteApiKey(id);
      setDeleteConfirmId(null);
      setSuccess("API key deleted successfully");
      await loadApiKeys();
    } catch (err) {
      setError("Failed to delete API key");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(
      () => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      },
      () => {
        setError("Failed to copy to clipboard");
      },
    );
  };

  const formatDate = (date: string | null) => {
    if (!date) return "Never";
    return format(date, "MMM d, yyyy h:mm a");
  };

  const isExpired = (expiresAt: string | null) => {
    if (!expiresAt) return false;
    return new Date() > new Date(expiresAt);
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">API Keys</h1>
        {!showCreateForm && !newApiKey && (
          <button
            onClick={() => setShowCreateForm(true)}
            className="flex items-center rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            <PlusIcon className="mr-2 h-5 w-5" />
            Create API Key
          </button>
        )}
      </div>

      {error && <Error>{error}</Error>}
      {success && <Success>{success}</Success>}

      {newApiKey && (
        <div className="mb-8 rounded-md border border-green-300 bg-green-50 p-4 dark:border-green-700 dark:bg-green-900/20">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-green-800 dark:text-green-300">
              API Key Created
            </h2>
            <button
              onClick={() => setNewApiKey(null)}
              className="text-green-800 hover:text-green-900 dark:text-green-300 dark:hover:text-green-200"
            >
              &times;
            </button>
          </div>
          <p className="mb-2 text-sm text-green-700 dark:text-green-300">
            Make sure to copy your API key now. You won't be able to see it
            again!
          </p>
          <div className="flex items-center">
            <code className="flex-grow rounded border bg-white p-2 font-mono text-sm dark:border-slate-700 dark:bg-slate-800">
              {newApiKey.key}
            </code>
            <button
              onClick={() => copyToClipboard(newApiKey.key)}
              className="ml-2 rounded bg-green-600 p-2 text-white hover:bg-green-700"
              title="Copy to clipboard"
            >
              {copied ? (
                <CheckIcon className="h-5 w-5" />
              ) : (
                <ClipboardIcon className="h-5 w-5" />
              )}
            </button>
          </div>
        </div>
      )}

      {showCreateForm && (
        <div className="mb-8 rounded-md border bg-gray-50 p-4 dark:border-slate-700 dark:bg-slate-800/50">
          <h2 className="mb-4 text-lg font-semibold">Create New API Key</h2>
          <form onSubmit={(event) => void handleCreateKey(event)}>
            <div className="mb-4">
              <label
                htmlFor="keyName"
                className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                Key Name
              </label>
              <input
                type="text"
                id="keyName"
                value={newKeyName}
                onChange={(e) => setNewKeyName(e.target.value)}
                className="w-full rounded border p-2 dark:border-slate-600 dark:bg-slate-700"
                placeholder="My API Key"
                required
              />
            </div>
            <div className="mb-4">
              <label
                htmlFor="expiration"
                className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300"
              >
                Expires In (Days)
              </label>
              <select
                id="expiration"
                value={expiresInDays === null ? "" : expiresInDays}
                onChange={(e) => {
                  const val = e.target.value;
                  setExpiresInDays(val === "" ? null : parseInt(val, 10));
                }}
                className="w-full rounded border p-2 dark:border-slate-600 dark:bg-slate-700"
              >
                <option value="">Never</option>
                <option value="7">7 days</option>
                <option value="30">30 days</option>
                <option value="90">90 days</option>
                <option value="365">1 year</option>
              </select>
            </div>
            <div className="flex justify-end space-x-2">
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="rounded border px-4 py-2 hover:bg-gray-100 dark:border-slate-600 dark:hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
                disabled={loading}
              >
                {loading ? "Creating..." : "Create"}
              </button>
            </div>
          </form>
        </div>
      )}

      {loading && !newApiKey && !showCreateForm ? (
        <div className="py-8 text-center">Loading...</div>
      ) : apiKeys.length === 0 ? (
        <div className="py-8 text-center text-gray-500 dark:text-gray-400">
          No API keys found. Create one to get started.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full overflow-hidden rounded-lg border bg-white dark:border-slate-700 dark:bg-slate-800">
            <thead className="bg-gray-50 dark:bg-slate-700">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">
                  Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">
                  Created
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">
                  Expires
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">
                  Last Used
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 dark:divide-slate-700">
              {apiKeys.map((key) => (
                <tr
                  key={key.id}
                  className="hover:bg-gray-50 dark:hover:bg-slate-700/50"
                >
                  <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-gray-900 dark:text-gray-200">
                    {key.name}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 dark:text-gray-400">
                    {formatDate(key.createdAt)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    {key.expiresAt ? (
                      <span
                        className={
                          isExpired(key.expiresAt)
                            ? "text-red-500 dark:text-red-400"
                            : "text-gray-500 dark:text-gray-400"
                        }
                      >
                        {isExpired(key.expiresAt) && (
                          <ExclamationTriangleIcon className="mr-1 inline h-4 w-4" />
                        )}
                        {formatDate(key.expiresAt)}
                      </span>
                    ) : (
                      <span className="text-gray-500 dark:text-gray-400">
                        Never
                      </span>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 dark:text-gray-400">
                    {formatDate(key.lastUsedAt)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                    {deleteConfirmId === key.id ? (
                      <div className="flex items-center justify-end space-x-2">
                        <span className="text-xs text-red-600 dark:text-red-400">
                          Confirm?
                        </span>
                        <button
                          onClick={() => void handleDeleteKey(key.id)}
                          className="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
                        >
                          Yes
                        </button>
                        <button
                          onClick={() => setDeleteConfirmId(null)}
                          className="text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
                        >
                          No
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setDeleteConfirmId(key.id)}
                        className="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
                        title="Delete API Key"
                      >
                        <TrashIcon className="h-5 w-5" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
