import { ReactNode } from "react";

const Modal = (props: {
  children: ReactNode;
  setVisible: (visible: boolean) => void;
}) => {
  return (
    <div
      className="fixed inset-0 z-50 h-full w-full overflow-y-auto bg-gray-600 bg-opacity-50"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          props.setVisible(false);
        }
      }}
    >
      <div className="mx-auto mt-20 max-w-xl pb-20">{props.children}</div>
    </div>
  );
};

export default Modal;
