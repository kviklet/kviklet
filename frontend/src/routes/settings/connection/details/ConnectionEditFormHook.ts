import { useForm, UseFormReturn } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";

type ConnectionFormType = "DATASOURCE" | "KUBERNETES";

interface UseConnectionFormProps<T extends z.ZodTypeAny> {
  initialValues: z.infer<T>;
  schema: T;
  onSubmit: (
    data: z.infer<T> & { connectionType: ConnectionFormType },
  ) => Promise<void>;
  connectionType: ConnectionFormType;
}

export function useConnectionForm<T extends z.ZodTypeAny>({
  initialValues,
  schema,
  onSubmit,
  connectionType,
}: UseConnectionFormProps<T>): UseFormReturn<z.infer<T>> & {
  handleFormSubmit: () => Promise<void>;
} {
  const form = useForm<z.infer<T>>({
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const handleFormSubmit = async (): Promise<void> => {
    try {
      const isValid = await form.trigger();
      if (isValid) {
        const formData = form.getValues();
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
