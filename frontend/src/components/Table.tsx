import { SelectExecuteResponse } from "../api/ExecutionRequestApi";
import DataTable from "react-data-table-component";

const Table: React.FC<{ data: SelectExecuteResponse }> = ({ data }) => {
  return (
    <DataTable
      columns={data.columns.map((column) => ({
        name: column.label,
        selector: (row: any) => row[column.label],
        sortable: true,
      }))}
      data={data.data.map((row, index) => ({ ...row, id: index }))}
      pagination
      dense
    />
  );
};

export default Table;
