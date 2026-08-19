import { ReactNode, useContext, useEffect, useState } from "react";
import { Popover, PopoverButton, PopoverPanel } from "@headlessui/react";
import {
  CheckIcon,
  ChevronDownIcon,
  ClockIcon,
  XMarkIcon,
} from "@heroicons/react/20/solid";
import { getConnections, ConnectionResponse } from "../api/DatasourceApi";
import { fetchUsers, UserResponse } from "../api/UserApi";
import { isApiErrorResponse } from "../api/Errors";
import { UserStatusContext } from "../components/UserStatusProvider";
import Tooltip from "../components/Tooltip";
import InitialBubble from "../components/InitialBubble";

interface RequestListFilters {
  connectionIds: string[];
  authorId: string | null;
  createdFrom: string | null;
  createdTo: string | null;
  onlyPending: boolean;
}

const emptyFilters: RequestListFilters = {
  connectionIds: [],
  authorId: null,
  createdFrom: null,
  createdTo: null,
  onlyPending: false,
};

const hasActiveFilters = (filters: RequestListFilters): boolean =>
  filters.connectionIds.length > 0 ||
  filters.authorId !== null ||
  filters.createdFrom !== null ||
  filters.createdTo !== null ||
  filters.onlyPending;

function formatDay(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    ...(sameYear ? {} : { year: "numeric" }),
  });
}

function dateRangeLabel(from: string | null, to: string | null): string {
  if (from && to) return `${formatDay(from)} – ${formatDay(to)}`;
  if (from) return `From ${formatDay(from)}`;
  if (to) return `Until ${formatDay(to)}`;
  return "Created";
}

const pillBaseClasses =
  "inline-flex h-7 items-center gap-1 rounded-md pl-2.5 text-xs font-medium ring-1 ring-inset transition-colors";
const pillInactiveClasses =
  "text-slate-600 ring-slate-300 hover:bg-white dark:text-slate-300 dark:ring-slate-700 dark:hover:bg-slate-900";
const pillActiveClasses =
  "bg-indigo-50 text-indigo-600 ring-indigo-600/30 dark:bg-indigo-400/10 dark:text-indigo-400 dark:ring-indigo-400/30";

function FilterPill({
  label,
  active,
  onClear,
  testId,
}: {
  label: string;
  active: boolean;
  onClear: () => void;
  testId: string;
}) {
  return (
    <PopoverButton
      data-testid={testId}
      className={`${pillBaseClasses} ${
        active ? `pr-1.5 ${pillActiveClasses}` : `pr-2 ${pillInactiveClasses}`
      }`}
    >
      <span className="max-w-[7rem] truncate">{label}</span>
      {active ? (
        <span
          role="button"
          aria-label={`Clear ${label} filter`}
          className="rounded p-0.5 hover:bg-indigo-100 dark:hover:bg-indigo-400/20"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onClear();
          }}
        >
          <XMarkIcon className="h-3.5 w-3.5" />
        </span>
      ) : (
        <ChevronDownIcon className="h-4 w-4 text-slate-400 dark:text-slate-500" />
      )}
    </PopoverButton>
  );
}

// Plain CSS positioning instead of the anchor prop: floating-ui's measuring
// loop never converges in jsdom, which hangs component tests.
const panelClasses =
  "absolute left-0 top-full z-10 mt-1 w-64 rounded-md border border-slate-200 bg-white p-1 shadow-lg dark:border-slate-700 dark:bg-slate-900";

function OptionRow({
  label,
  selected,
  onClick,
  testId,
  leading,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
  testId?: string;
  leading?: ReactNode;
}) {
  return (
    <button
      type="button"
      data-testid={testId}
      onClick={onClick}
      className="flex w-full items-center gap-2 rounded px-2.5 py-1.5 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
    >
      {leading}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {selected && (
        <CheckIcon className="h-4 w-4 shrink-0 text-indigo-600 dark:text-indigo-400" />
      )}
    </button>
  );
}

const dateInputClasses =
  "mt-1 block w-full rounded-md border-slate-300 py-1.5 text-sm text-slate-900 focus:border-indigo-600 focus:ring-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:[color-scheme:dark]";

function RequestFilterBar({
  filters,
  onChange,
}: {
  filters: RequestListFilters;
  onChange: (filters: RequestListFilters) => void;
}) {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const { userStatus } = useContext(UserStatusContext);
  const currentUserId = userStatus === false ? undefined : userStatus?.id;
  const currentUserName =
    userStatus === false
      ? undefined
      : userStatus?.fullName ?? userStatus?.email;

  useEffect(() => {
    // Both lists are permission-gated; a user who can't load them still gets
    // the remaining filters, so failures stay silent.
    const load = async () => {
      const [connectionsResponse, usersResponse] = await Promise.all([
        getConnections(),
        fetchUsers(),
      ]);
      if (!isApiErrorResponse(connectionsResponse)) {
        setConnections(connectionsResponse);
      }
      if (!isApiErrorResponse(usersResponse)) {
        setUsers(usersResponse.users);
      }
    };
    void load();
  }, []);

  const toggleConnection = (id: string) => {
    const connectionIds = filters.connectionIds.includes(id)
      ? filters.connectionIds.filter((existing) => existing !== id)
      : [...filters.connectionIds, id];
    onChange({ ...filters, connectionIds });
  };

  const connectionLabel =
    filters.connectionIds.length === 0
      ? "Connection"
      : filters.connectionIds.length === 1
      ? connections.find((c) => c.id === filters.connectionIds[0])
          ?.displayName ?? "1 connection"
      : `${filters.connectionIds.length} connections`;

  const authorLabel =
    filters.authorId === null
      ? "Author"
      : filters.authorId === currentUserId
      ? "Your requests"
      : users.find((u) => u.id === filters.authorId)?.fullName ??
        users.find((u) => u.id === filters.authorId)?.email ??
        "1 author";

  const otherUsers = users.filter((u) => u.id !== currentUserId);

  return (
    <div className="flex flex-wrap items-center gap-2">
      {connections.length > 0 && (
        <Popover className="relative">
          <FilterPill
            label={connectionLabel}
            active={filters.connectionIds.length > 0}
            onClear={() => onChange({ ...filters, connectionIds: [] })}
            testId="filter-connection"
          />
          <PopoverPanel className={panelClasses}>
            <div className="max-h-64 overflow-y-auto">
              {connections.map((connection) => (
                <OptionRow
                  key={connection.id}
                  label={connection.displayName}
                  selected={filters.connectionIds.includes(connection.id)}
                  onClick={() => toggleConnection(connection.id)}
                  testId={`filter-connection-${connection.displayName}`}
                />
              ))}
            </div>
          </PopoverPanel>
        </Popover>
      )}

      {currentUserId && (
        <Popover className="relative">
          <FilterPill
            label={authorLabel}
            active={filters.authorId !== null}
            onClear={() => onChange({ ...filters, authorId: null })}
            testId="filter-author"
          />
          <PopoverPanel className={panelClasses}>
            {({ close }) => (
              <div className="max-h-64 overflow-y-auto">
                <OptionRow
                  label="Your requests"
                  leading={
                    <InitialBubble
                      name={currentUserName}
                      className="!h-5 !w-5 shrink-0 !text-[9px]"
                    />
                  }
                  selected={filters.authorId === currentUserId}
                  onClick={() => {
                    onChange({
                      ...filters,
                      authorId:
                        filters.authorId === currentUserId
                          ? null
                          : currentUserId,
                    });
                    close();
                  }}
                  testId="filter-author-mine"
                />
                {otherUsers.length > 0 && (
                  <div className="mx-2.5 my-1 border-t border-slate-200 dark:border-slate-700" />
                )}
                {otherUsers.map((user) => (
                  <OptionRow
                    key={user.id}
                    label={user.fullName ?? user.email}
                    leading={
                      <InitialBubble
                        name={user.fullName ?? user.email}
                        className="!h-5 !w-5 shrink-0 !text-[9px]"
                      />
                    }
                    selected={filters.authorId === user.id}
                    onClick={() => {
                      onChange({
                        ...filters,
                        authorId: filters.authorId === user.id ? null : user.id,
                      });
                      close();
                    }}
                  />
                ))}
              </div>
            )}
          </PopoverPanel>
        </Popover>
      )}

      <Popover className="relative">
        <FilterPill
          label={dateRangeLabel(filters.createdFrom, filters.createdTo)}
          active={filters.createdFrom !== null || filters.createdTo !== null}
          onClear={() =>
            onChange({ ...filters, createdFrom: null, createdTo: null })
          }
          testId="filter-created"
        />
        <PopoverPanel className={`${panelClasses} p-3`}>
          <div className="flex flex-col gap-2">
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300">
              From
              <input
                type="date"
                data-testid="filter-created-from"
                value={filters.createdFrom ?? ""}
                max={filters.createdTo ?? undefined}
                onChange={(e) =>
                  onChange({
                    ...filters,
                    createdFrom: e.target.value || null,
                  })
                }
                className={dateInputClasses}
              />
            </label>
            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300">
              To
              <input
                type="date"
                data-testid="filter-created-to"
                value={filters.createdTo ?? ""}
                min={filters.createdFrom ?? undefined}
                onChange={(e) =>
                  onChange({
                    ...filters,
                    createdTo: e.target.value || null,
                  })
                }
                className={dateInputClasses}
              />
            </label>
          </div>
        </PopoverPanel>
      </Popover>

      <Tooltip position="bottom" content="Show only pending requests">
        <button
          type="button"
          data-testid="filter-pending"
          onClick={() =>
            onChange({ ...filters, onlyPending: !filters.onlyPending })
          }
          className={`${pillBaseClasses} pr-2.5 ${
            filters.onlyPending ? pillActiveClasses : pillInactiveClasses
          }`}
        >
          <ClockIcon className="h-3.5 w-3.5" />
          Pending
        </button>
      </Tooltip>

      {hasActiveFilters(filters) && (
        <button
          type="button"
          data-testid="filter-clear-all"
          onClick={() => onChange(emptyFilters)}
          className="text-xs font-medium text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
        >
          Clear filters
        </button>
      )}
    </div>
  );
}

export { RequestFilterBar, emptyFilters, hasActiveFilters };
export type { RequestListFilters };
