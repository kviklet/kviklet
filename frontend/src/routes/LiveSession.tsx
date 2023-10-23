import { Editor } from "../components/Editor";

export default function LiveSession() {
  return (
    <div className="w-full flex">
      <div className="mx-auto w-2/3 h-screen">
        Live Session here
        <Editor></Editor>
      </div>
    </div>
  );
}
