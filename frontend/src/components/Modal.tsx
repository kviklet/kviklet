const Modal = (props: {
  children: React.ReactNode;
  setVisible: (visible: boolean) => void;
}) => {
  return (
    <div
      className="fixed inset-0 h-full w-full overflow-y-auto bg-gray-600 bg-opacity-50"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          props.setVisible(false);
        }
      }}
    >
      <div className="relative top-20 mx-auto max-w-xl">{props.children}</div>
    </div>
  );
};

export default Modal;
