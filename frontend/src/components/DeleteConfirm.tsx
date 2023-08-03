import Button from "./Button";

const DeleteConfirm = (props: {
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}) => {
  return (
    <div className="w-2xl shadow p-3 bg-white rounded">
      <div className="flex flex-col mb-3">
        <h1 className="text-2xl font-bold">{props.title}</h1>
        <p className="text-gray-700">{props.message}</p>

        <div className="flex flex-row mt-3">
          <Button className="ml-auto" onClick={props.onCancel}>
            Cancel
          </Button>
          <Button className="ml-2" onClick={props.onConfirm} type="danger">
            Confirm
          </Button>
        </div>
      </div>
    </div>
  );
};

export default DeleteConfirm;
