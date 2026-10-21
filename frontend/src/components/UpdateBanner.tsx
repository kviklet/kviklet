import { ReactNode, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  ArrowTopRightOnSquareIcon,
  ArrowUpCircleIcon,
  ChevronDownIcon,
  XMarkIcon,
} from "@heroicons/react/20/solid";
import useConfig from "./ConfigProvider";
import { UpdateInfo } from "../api/ConfigApi";

const dismissedKey = "updateBannerDismissed";
// Dismissing only snoozes the banner: updates often carry security fixes, so it comes back.
const snoozeMs = 7 * 24 * 60 * 60 * 1000;
const repoUrl = "https://github.com/kviklet/kviklet";

type Dismissal = { target: string; at: number };

// What the banner points at; a newer release or commit brings it back before the snooze ends.
const targetOf = (update: UpdateInfo) =>
  update.channel === "RELEASE"
    ? `release:${update.latestVersion ?? ""}`
    : `main:${update.commits[0]?.sha ?? update.behindBy ?? ""}`;

const isDismissed = (target: string) => {
  try {
    const raw = localStorage.getItem(dismissedKey);
    if (!raw) return false;
    const dismissal = JSON.parse(raw) as Partial<Dismissal>;
    return (
      dismissal.target === target &&
      typeof dismissal.at === "number" &&
      Date.now() - dismissal.at < snoozeMs
    );
  } catch {
    return false;
  }
};

const linkClass =
  "text-blue-600 hover:text-blue-800 hover:underline dark:text-blue-400 dark:hover:text-blue-300";

// Release notes are rendered without raw HTML (skipHtml), and react-markdown drops
// javascript: and other unsafe link targets, so notes can't inject anything into the page.
const markdownComponents = {
  h1: ({ children }: { children: ReactNode }) => (
    <h4 className="mb-1 mt-3 font-semibold">{children}</h4>
  ),
  h2: ({ children }: { children: ReactNode }) => (
    <h4 className="mb-1 mt-3 font-semibold">{children}</h4>
  ),
  h3: ({ children }: { children: ReactNode }) => (
    <h4 className="mb-1 mt-3 font-semibold">{children}</h4>
  ),
  p: ({ children }: { children: ReactNode }) => (
    <p className="my-1">{children}</p>
  ),
  ul: ({ children }: { children: ReactNode }) => (
    <ul className="my-1 ml-5 list-disc">{children}</ul>
  ),
  ol: ({ children }: { children: ReactNode }) => (
    <ol className="my-1 ml-5 list-decimal">{children}</ol>
  ),
  a: ({ href, children }: { href?: string; children: ReactNode }) => (
    <a href={href} target="_blank" rel="noreferrer" className={linkClass}>
      {children}
    </a>
  ),
  code: ({ children }: { children: ReactNode }) => (
    <code className="rounded bg-slate-100 px-1 font-mono text-xs dark:bg-slate-800">
      {children}
    </code>
  ),
  pre: ({ children }: { children: ReactNode }) => (
    <pre className="my-1 overflow-x-auto">{children}</pre>
  ),
  img: ({ src, alt }: { src?: string; alt?: string }) => (
    <img src={src} alt={alt} className="my-2 max-w-full" />
  ),
};

export const ReleaseNotes = ({ body }: { body: string }) => (
  <ReactMarkdown
    remarkPlugins={[remarkGfm]}
    skipHtml
    components={markdownComponents}
  >
    {body}
  </ReactMarkdown>
);

// Squash-merged commits end in "(#123)"; link that to the pull request.
const CommitMessage = ({ message }: { message: string }) => {
  const match = /^(.*)\(#(\d+)\)\s*$/.exec(message);
  if (!match) return <>{message}</>;
  return (
    <>
      {match[1]}(
      <a
        href={`${repoUrl}/pull/${match[2]}`}
        target="_blank"
        rel="noreferrer"
        className={linkClass}
      >
        #{match[2]}
      </a>
      )
    </>
  );
};

const plural = (count: number, word: string) =>
  `${count} ${word}${count === 1 ? "" : "s"}`;

const Changelog = ({ update }: { update: UpdateInfo }) => {
  if (update.channel === "RELEASE") {
    return (
      <div className="space-y-4">
        {update.releases.map((release) => (
          <section key={release.tag} data-testid="update-release">
            <h3 className="flex items-baseline gap-2 border-b border-slate-200 pb-1 dark:border-slate-700">
              <a
                href={release.url}
                target="_blank"
                rel="noreferrer"
                className={`font-semibold ${linkClass}`}
              >
                {release.name ?? release.tag}
              </a>
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {release.publishedAt.toLocaleDateString()}
              </span>
            </h3>
            <div className="text-slate-700 dark:text-slate-300">
              <ReleaseNotes body={release.body} />
            </div>
          </section>
        ))}
      </div>
    );
  }
  const hidden = (update.behindBy ?? 0) - update.commits.length;
  return (
    <div>
      <ul className="divide-y divide-slate-200 dark:divide-slate-700">
        {update.commits.map((commit) => (
          <li
            key={commit.sha}
            className="flex items-baseline gap-3 py-1.5"
            data-testid="update-commit"
          >
            <a
              href={commit.url}
              target="_blank"
              rel="noreferrer"
              className={`shrink-0 font-mono text-xs ${linkClass}`}
            >
              {commit.sha.slice(0, 7)}
            </a>
            <span className="min-w-0 flex-1 text-slate-700 dark:text-slate-300">
              <CommitMessage message={commit.message} />
            </span>
            <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
              {commit.committedAt.toLocaleDateString()}
            </span>
          </li>
        ))}
      </ul>
      {hidden > 0 && (
        <a
          href={update.url}
          target="_blank"
          rel="noreferrer"
          className={`mt-2 inline-block ${linkClass}`}
        >
          and {plural(hidden, "older commit")} on GitHub
        </a>
      )}
    </div>
  );
};

/**
 * Tells everyone (also on the login page) that a newer Kviklet is out, so users can nudge
 * whoever runs the instance. Closing it snoozes it in this browser for a week, or until
 * something newer comes out.
 */
const UpdateBanner = () => {
  const { config } = useConfig();
  const update = config?.update;
  const [dismissedTarget, setDismissedTarget] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);

  if (!update) return null;
  const target = targetOf(update);
  if (dismissedTarget === target || isDismissed(target)) return null;

  const dismiss = () => {
    try {
      const dismissal: Dismissal = { target, at: Date.now() };
      localStorage.setItem(dismissedKey, JSON.stringify(dismissal));
    } catch {
      // Without storage it comes back on the next visit, which is all that is lost.
    }
    setDismissedTarget(target);
  };

  const summary =
    update.channel === "RELEASE" ? (
      <>
        <span className="font-semibold">
          Kviklet {update.latestVersion} is available.
        </span>{" "}
        This instance runs {config.version}
        {update.releases.length > 1 &&
          `, ${plural(update.releases.length, "release")} behind`}
        .
      </>
    ) : (
      <>
        <span className="font-semibold">
          This build is {plural(update.behindBy ?? 0, "commit")} behind main.
        </span>
      </>
    );

  return (
    <div
      className="border-b border-blue-200 bg-blue-50 text-sm dark:border-blue-800 dark:bg-blue-900/20"
      data-testid="update-banner"
    >
      <div className="mx-4 px-2 py-2 sm:px-8">
        <div className="flex items-center gap-2">
          <ArrowUpCircleIcon className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400" />
          <span className="text-slate-700 dark:text-slate-200">{summary}</span>
          <button
            type="button"
            onClick={() => setExpanded(!expanded)}
            className="flex shrink-0 items-center text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
            aria-expanded={expanded}
            data-testid="update-banner-expand"
          >
            {expanded ? "Hide changes" : "What's new"}
            <ChevronDownIcon
              className={`h-4 w-4 transition-transform ${
                expanded ? "rotate-180" : ""
              }`}
            />
          </button>
          <a
            href={update.url}
            target="_blank"
            rel="noreferrer"
            className="hidden shrink-0 items-center gap-1 text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300 sm:flex"
          >
            View on GitHub
            <ArrowTopRightOnSquareIcon className="h-3.5 w-3.5" />
          </a>
          <button
            type="button"
            onClick={dismiss}
            className="ml-auto shrink-0 rounded p-0.5 text-slate-400 hover:bg-blue-100 hover:text-slate-700 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-200"
            title="Dismiss for a week"
            data-testid="update-banner-dismiss"
          >
            <XMarkIcon className="h-4 w-4" />
          </button>
        </div>
        {expanded && (
          <div
            className="mb-2 mt-3 max-h-[60vh] overflow-y-auto rounded-md border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
            data-testid="update-changelog"
          >
            <Changelog update={update} />
          </div>
        )}
      </div>
    </div>
  );
};

export default UpdateBanner;
