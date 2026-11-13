import { Select } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError } from "../api/client";
import { locationOptions } from "./location-options";
import { listLocations, type LocationNode } from "./locations-api";

export interface LocationPickerProps {
  disabled?: boolean;
  id?: string;
  onChange: (locationId: string | null) => void;
  value: string | null;
}

export function LocationPicker({
  disabled = false,
  id,
  onChange,
  value,
}: LocationPickerProps) {
  const { t } = useTranslation("library");
  const [locations, setLocations] = useState<LocationNode[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    listLocations(controller.signal)
      .then((nodes) => {
        setLocations(nodes);
      })
      .catch((cause) => {
        if (!isAbortError(cause)) {
          setLocations([]);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, []);

  const options = [
    { label: t("copy.locationUnset"), value: "" },
    ...locationOptions(locations),
  ];

  return (
    <Select
      ariaBusy={loading || undefined}
      ariaLabel={t("copy.location")}
      disabled={disabled || loading}
      id={id}
      onValueChange={(next) => {
        onChange(next.length === 0 ? null : next);
      }}
      options={options}
      placeholder={t("copy.locationUnset")}
      value={value ?? ""}
    />
  );
}