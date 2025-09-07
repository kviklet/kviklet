import React, { useState, useEffect, FormEvent } from "react";
import {
  ApiKeyResponse,
  ApiKeyWithSecretResponse,
  listApiKeys,
  createApiKey,
  deleteApiKey,
} from "../../api/ApiKeyApi";
import { Error } from "../../components/Alert";
import {
  ClipboardIcon,
  CheckIcon,
  ExclamationTriangleIcon,
} from "@heroicons/react/24/outline";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import Button from "../../components/Button";
import Modal from "../../components/Modal";
import SettingsTable, { Column } from "../../components/SettingsTable";

export default function ApiKeyPage() {
  const [apiKeys, setApiKeys] = useState<ApiKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newKeyName, setNewKeyName] = useState("");
  const [expiresInDays, setExpiresInDays] = useState<number | null>(30);
  const [newApiKey, setNewApiKey] = useState<ApiKeyWithSecretResponse | null>(
    null,
  );
  const [copied, setCopied] = useState(false);

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
        await loadApiKeys();
      }
    } catch (err) {
      setError("Failed to create API key");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteKey = async (key: ApiKeyResponse) => {
    const response = await deleteApiKey(key.id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to delete API key",
        text: response.message,
        type: "error",
      });
    } else {
      addNotification({
        title: "API Key deleted",
        text: `API key "${key.name}" has been deleted`,
        type: "info",
      });
      await loadApiKeys();
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

  const formatDate = (date: Date | null): string => {
    if (!date) return "Never";
    return new Date(date).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      hour12: true,
    });
  };

  const isExpired = (expiresAt: Date | null) => {
    if (!expiresAt) return false;
    return new Date() > new Date(expiresAt);
  };

  const columns: Column<ApiKeyResponse>[] = [
    {
      header: "Name",
      accessor: "name",
    },
    {
      header: "Created By",
      render: (key) => key.user.fullName || key.user.email,
    },
    {
      header: "Created",
      render: (key) => formatDate(key.createdAt),
    },
    {
      header: "Expires",
      render: (key) => {
        if (!key.expiresAt) {
          return (
            <span className="text-slate-500 dark:text-slate-400">Never</span>
          );
        }

        const expired = isExpired(key.expiresAt);
        return (
          <span
            className={
              expired
                ? "text-red-500 dark:text-red-400"
                : "text-slate-500 dark:text-slate-400"
            }
          >
            {expired && (
              <ExclamationTriangleIcon className="mr-1 inline h-4 w-4" />
            )}
            {formatDate(key.expiresAt)}
          </span>
        );
      },
    },
    {
      header: "Last Used",
      render: (key) => formatDate(key.lastUsedAt),
    },
  ];

  return (
    <div className="container mx-auto px-4 py-8">
      {error && (
        <div className="mb-4">
          <Error>{error}</Error>
        </div>
      )}

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
        <Modal setVisible={setShowCreateForm}>
          <div className="mb-8 rounded-md border bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900">
            <h2 className="mb-4 text-lg font-semibold">Create New API Key</h2>
            <form onSubmit={(event) => void handleCreateKey(event)}>
              <div className="mb-4">
                <label
                  htmlFor="keyName"
                  className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300"
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
                  className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300"
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
                <Button
                  htmlType="button"
                  onClick={() => setShowCreateForm(false)}
                >
                  Cancel
                </Button>
                <Button
                  htmlType="submit"
                  variant={loading ? "disabled" : "primary"}
                >
                  {loading ? "Creating..." : "Create"}
                </Button>
              </div>
            </form>
          </div>
        </Modal>
      )}

      {!newApiKey && !showCreateForm && (
        <SettingsTable
          title="API Keys"
          data={apiKeys}
          columns={columns}
          keyExtractor={(key) => key.id}
          onDelete={handleDeleteKey}
          onCreate={() => setShowCreateForm(true)}
          createButtonLabel="Create API Key"
          emptyMessage="No API keys found. Create one to get started."
          loading={loading}
          testId="api-keys-table"
        />
      )}
    </div>
  );
}
