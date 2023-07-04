const Modal = (props: {
  children: React.ReactNode;
  setVisible: (visible: boolean) => void;
}) => {
  return (
    <div
      className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          props.setVisible(false);
        }
      }}
    >
      <div className="relative top-20 mx-auto max-w-lg">{props.children}</div>
    </div>
  );
};

export default Modal;
