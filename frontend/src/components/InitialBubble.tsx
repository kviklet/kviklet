import { colorFromText } from "./ColorfulLabel";

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
      className={`rounded-full ${colorFromText(
        props.name || "",
      )} flex h-8 w-8 items-center justify-center text-sm ${
        props.className ?? ""
      }`}
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
