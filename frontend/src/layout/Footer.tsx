import useConfig from "../components/ConfigProvider";

function Footer() {
  const { config } = useConfig();

  if (!config) return null;

  return (
    <footer className="fixed bottom-0 left-0 right-0 border-t border-slate-200 bg-slate-50 px-4 py-2 text-center text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-400">
      <span>Kviklet v{config.version}</span>
      <span className="mx-2">|</span>
      <span>Build: {config.buildDate}</span>
      <span className="mx-2">|</span>
      <span>Commit: {config.gitCommit}</span>
    </footer>
  );
}

export default Footer;
