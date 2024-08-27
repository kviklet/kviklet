import { useEffect, useState } from "react";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import Table from "./Table";
import Button from "./Button";
import Toggle from "./Toggle";

const JsonViewer = ({ data }: { data: unknown }) => {
  const [expandedKeys, setExpandedKeys] = useState(new Set<string>());
  const [isCodeView, setIsCodeView] = useState(false);

  useEffect(() => {
    const getAllKeys = (obj: unknown, prefix = "") => {
      let keys = new Set<string>();
      if (Array.isArray(obj)) {
        keys.add(prefix);
        obj.forEach((item, index) => {
          keys = new Set([...keys, ...getAllKeys(item, `${prefix}-${index}`)]);
        });
      } else if (typeof obj === "object" && obj !== null) {
        keys.add(prefix);
        Object.entries(obj).forEach(([key, value]) => {
          keys = new Set([...keys, ...getAllKeys(value, `${prefix}-${key}`)]);
        });
      }
      return keys;
    };

    setExpandedKeys(getAllKeys(data, "root"));
  }, [data]);

  const toggleExpand = (key: string) => {
    const newExpandedKeys = new Set(expandedKeys);
    if (newExpandedKeys.has(key)) {
      newExpandedKeys.delete(key);
    } else {
      newExpandedKeys.add(key);
    }
    setExpandedKeys(newExpandedKeys);
  };

  const renderValue = (value: unknown, key = "", depth = 0) => {
    if (Array.isArray(value)) {
      return (
        <div className="ml-4">
          <span
            className="cursor-pointer text-blue-500 hover:text-blue-700"
            onClick={() => toggleExpand(key)}
          >
            {expandedKeys.has(key) ? "[-]" : "[+]"} Array ({value.length})
          </span>
          {expandedKeys.has(key) && (
            <div className="ml-4">
              {value.map((item, index) => (
                <div key={`${key}-${index}`}>
                  {renderValue(item, `${key}-${index}`, depth + 1)}
                </div>
              ))}
            </div>
          )}
        </div>
      );
    } else if (typeof value === "object" && value !== null) {
      return (
        <div className="ml-4">
          <span
            className="cursor-pointer text-blue-500 hover:text-blue-700"
            onClick={() => toggleExpand(key)}
          >
            {expandedKeys.has(key) ? "{-}" : "{+}"} Object
          </span>
          {expandedKeys.has(key) && (
            <div className="ml-4">
              {Object.entries(value).map(([k, v]) => (
                <div key={`${key}-${k}`} className="my-1">
                  <span className="font-semibold text-green-600">{k}:</span>{" "}
                  {renderValue(v, `${key}-${k}`, depth + 1)}
                </div>
              ))}
            </div>
          )}
        </div>
      );
    } else if (typeof value === "string") {
      return <span className="text-red-500">"{value}"</span>;
    } else if (typeof value === "number") {
      return <span className="text-purple-500">{value}</span>;
    } else if (typeof value === "boolean") {
      return <span className="text-yellow-500">{value.toString()}</span>;
    } else if (value === null) {
      return <span className="text-gray-500">null</span>;
    } else {
      return <span>{String(value)}</span>;
    }
  };

  const renderJsonCode = () => {
    return (
      <pre className="m-0 h-full w-full overflow-auto">
        <code>{JSON.stringify(data, null, 2)}</code>
      </pre>
    );
  };

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-lg bg-gray-100 font-mono text-sm shadow-md dark:border dark:border-slate-700 dark:bg-slate-950">
        <div className="overflow-auto">
          <div className="min-h-full p-4">
            {isCodeView ? (
              renderJsonCode()
            ) : (
              <div className="min-h-full">{renderValue(data, "root")}</div>
            )}
          </div>
        </div>
      </div>
      <div className="flex items-center justify-end space-x-2">
        <span className="text-sm text-slate-500 dark:text-slate-400">
          {isCodeView ? "Code View" : "Tree View"}
        </span>
        <Toggle
          active={isCodeView}
          onClick={() => setIsCodeView(!isCodeView)}
        />
      </div>
    </div>
  );
};

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
    <div
      className="flex w-screen flex-col justify-center"
      data-testid="result-component"
    >
      {resultList.length > 1 && (
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
      )}
      {selectedContent._type === "select" && <Table data={selectedContent} />}

      {selectedContent._type === "update" && (
        <div className="text-slate-500">
          {selectedContent.rowsUpdated} rows updated
        </div>
      )}
      {selectedContent._type === "error" && (
        <div className="text-red-500">{selectedContent.message}</div>
      )}
      {selectedContent._type === "documents" && (
        <JsonViewer data={selectedContent.documents} />
      )}
    </div>
  );
};

export default MultiResult;
