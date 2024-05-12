import { useState } from "react";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import Table from "./Table";
import Button from "./Button";

const MultiResult: React.FC<{ resultList: ExecuteResponseResult[] }> = ({
  resultList,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);

  const handleNext = () => {
    if (currentIndex < resultList.length - 1) {
      setCurrentIndex(currentIndex + 1);
    }
  };

  const handlePrevious = () => {
    if (currentIndex > 0) {
      setCurrentIndex(currentIndex - 1);
    }
  };

  const selectedContent = resultList[currentIndex];
  return (
    <div className="flex w-screen flex-col justify-center">
      <div className="flex justify-start space-x-2">
        <div className="my-1 text-slate-700 dark:text-slate-400">
          {currentIndex + 1 + "/" + resultList.length}
        </div>
        <Button size="sm" onClick={handlePrevious}>
          Previous
        </Button>
        <Button size="sm" onClick={handleNext}>
          Next
        </Button>
      </div>
      {selectedContent._type === "select" && <Table data={selectedContent} />}

      {selectedContent._type === "update" && (
        <div className="text-slate-500">
          {selectedContent.rowsUpdated} rows updated
        </div>
      )}
      {selectedContent._type === "error" && (
        <div className="text-red-500">{selectedContent.message}</div>
      )}
    </div>
  );
};

export default MultiResult;
