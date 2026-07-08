import { Fragment } from "react";
import { Link } from "react-router-dom";
import { ChevronRightIcon } from "@heroicons/react/20/solid";

interface BreadcrumbItem {
  label: string;
  to?: string;
}

function Breadcrumbs({
  items,
  className,
}: {
  items: BreadcrumbItem[];
  className?: string;
}) {
  return (
    <nav
      aria-label="Breadcrumb"
      className={`flex items-center gap-1 text-sm text-slate-500 dark:text-slate-400 ${
        className ?? ""
      }`}
    >
      {items.map((item, index) => (
        <Fragment key={index}>
          {index > 0 && <ChevronRightIcon className="h-4 w-4 shrink-0" />}
          {item.to ? (
            <Link
              to={item.to}
              className="min-w-0 truncate hover:text-slate-900 dark:hover:text-slate-50"
            >
              {item.label}
            </Link>
          ) : (
            <span className="min-w-0 truncate text-slate-900 dark:text-slate-50">
              {item.label}
            </span>
          )}
        </Fragment>
      ))}
    </nav>
  );
}

export default Breadcrumbs;
