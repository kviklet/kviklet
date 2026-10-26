import {
  DefaultValues,
  FieldValues,
  useForm,
  UseFormReturn,
} from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

type ConnectionFormType = "DATASOURCE" | "KUBERNETES";

interface UseConnectionFormProps<Input extends FieldValues, Output> {
  initialValues: Input;
  schema: z.ZodType<Output, Input>;
  onSubmit: (
    data: Output & { connectionType: ConnectionFormType },
  ) => Promise<void>;
  connectionType: ConnectionFormType;
}

export function useConnectionForm<Input extends FieldValues, Output>({
  initialValues,
  schema,
  onSubmit,
  connectionType,
}: UseConnectionFormProps<Input, Output>): UseFormReturn<
  Input,
  unknown,
  Output
> & {
  handleFormSubmit: () => Promise<void>;
} {
  const form = useForm<Input, unknown, Output>({
    resolver: zodResolver(schema),
    defaultValues: initialValues as DefaultValues<Input>,
  });

  const handleFormSubmit = async (): Promise<void> => {
    try {
      const isValid = await form.trigger();
      if (isValid) {
        // Deliberately the raw values, not the parsed output: a cleared number field
        // stays "" (which the backend reads as "unchanged"), whereas z.coerce would
        // turn it into 0. Typed numbers stay strings; the backend coerces them.
        const formData = form.getValues() as unknown as Output;
        await onSubmit({ ...formData, connectionType });
      }
    } catch (error) {
      console.error(error);
    }
  };

  return {
    ...form,
    handleFormSubmit,
  };
}
