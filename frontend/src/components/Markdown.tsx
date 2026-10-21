import { ReactNode } from "react";

export const linkClass =
  "text-blue-600 hover:text-blue-800 hover:underline dark:text-blue-400 dark:hover:text-blue-300";

const githubLink =
  /^https:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/(pull|issues|compare)\/(.+)$/;

/**
 * Bare GitHub links read the way GitHub shows them: "#585" for a pull request or issue,
 * "0.9.1...0.9.2" for a comparison. Other repositories keep their name, e.g. "owner/repo#12".
 */
export const shortGithubLinkText = (href: string): string | null => {
  const match = githubLink.exec(href);
  if (!match) return null;
  const [, owner, repo, kind, rest] = match;
  if (kind === "compare") return rest;
  if (!/^\d+$/.test(rest)) return null;
  const prefix =
    `${owner}/${repo}` === "kviklet/kviklet" ? "" : `${owner}/${repo}`;
  return `${prefix}#${rest}`;
};

const isBareLink = (href: string | undefined, children: ReactNode) => {
  const text = Array.isArray(children) ? (children as ReactNode[]) : [children];
  return href !== undefined && text.length === 1 && text[0] === href;
};

/**
 * The shared look of rendered Markdown (comments, release notes). Pass it to ReactMarkdown
 * as `components`; Tailwind's reset would otherwise strip headings, lists and links bare.
 */
export const markdownComponents = {
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
  a: ({ href, children }: { href?: string; children: ReactNode }) => {
    const short = isBareLink(href, children) && shortGithubLinkText(href!);
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer"
        className={linkClass}
        title={short ? href : undefined}
      >
        {short || children}
      </a>
    );
  },
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
