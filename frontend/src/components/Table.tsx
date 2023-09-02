import { useContext } from "react";
import { SelectExecuteResponse } from "../api/ExecutionRequestApi";
import DataTable, { createTheme } from "react-data-table-component";
import { ThemeContext, ThemeStatusContext } from "./ThemeStatusProvider";

createTheme(
  "myDarkTheme",
  {
    text: {
      primary: "slate-50",
      secondary: "slate-50",
    },
    background: {
      default: "#002b36",
    },
    context: {
      background: "#cb4b16",
      text: "#FFFFFF",
    },
    divider: {
      default: "#073642",
    },
    action: {
      button: "rgba(0,0,0,.54)",
      hover: "rgba(0,0,0,.08)",
      disabled: "rgba(0,0,0,.12)",
    },
  },
  "dark"
);

const Table: React.FC<{ data: SelectExecuteResponse }> = ({ data }) => {
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  return (
    <DataTable
      columns={data.columns.map((column) => ({
        name: column.label,
        selector: (row: any) => row[column.label],
        sortable: true,
      }))}
      theme={currentTheme === "light" ? "light" : "myDarkTheme"}
      data={data.data.map((row, index) => ({ ...row, id: index }))}
      pagination
      dense
    />
  );
};

export default Table;
