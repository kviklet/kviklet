const avatarColors = [
  "bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400",
  "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400",
  "bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400",
  "bg-rose-100 text-rose-700 dark:bg-rose-500/20 dark:text-rose-400",
  "bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-400",
  "bg-cyan-100 text-cyan-700 dark:bg-cyan-500/20 dark:text-cyan-400",
  "bg-orange-100 text-orange-700 dark:bg-orange-500/20 dark:text-orange-400",
  "bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-400",
];

function avatarColorFromName(name: string): string {
  let hash = 5381;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) + hash + name.charCodeAt(i);
  }
  return avatarColors[Math.abs(hash) % avatarColors.length];
}

function firstTwoLetters(input: string): string {
  const words = input.split(" ");
  let result = "";

  for (let i = 0; i < words.length; i++) {
    if (result.length < 2) {
      result += words[i][0];
    } else {
      break;
    }
  }

  return result;
}

const InitialBubble = (props: { name?: string | null; className?: string }) => {
  return (
    <div
      className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold ${avatarColorFromName(
        props.name || "",
      )} ${props.className ?? ""}`}
    >
      {firstTwoLetters(props.name ?? "")}
    </div>
  );
};

const AbsoluteInitialBubble = (props: {
  name?: string | null;
  className?: string;
}) => {
  return (
    <InitialBubble
      name={props.name}
      className={`absolute -left-12 ${props.className}`}
    />
  );
};

export { AbsoluteInitialBubble };
export default InitialBubble;
