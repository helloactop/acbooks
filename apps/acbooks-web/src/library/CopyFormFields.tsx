import { Field, Input } from "@acbooks/ui";
import { useTranslation } from "react-i18next";

import type { CopyDraft } from "./copy-draft";
import { LocationPicker } from "./LocationPicker";

export interface CopyFormFieldsProps {
  disabled?: boolean;
  draft: CopyDraft;
  idPrefix: string;
  onChange: (draft: CopyDraft) => void;
}

export function CopyFormFields({
  disabled = false,
  draft,
  idPrefix,
  onChange,
}: CopyFormFieldsProps) {
  const { t } = useTranslation("library");
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field htmlFor={`${idPrefix}-location`} label={t("copy.location")}>
        <LocationPicker
          disabled={disabled}
          id={`${idPrefix}-location`}
          onChange={(locationId) => {
            onChange({ ...draft, locationId });
          }}
          value={draft.locationId}
        />
      </Field>
      <Field htmlFor={`${idPrefix}-acquired`} label={t("copy.acquiredAt")}>
        <Input
          disabled={disabled}
          id={`${idPrefix}-acquired`}
          max="2999-12-31"
          min="1900-01-01"
          onChange={(event) => {
            onChange({ ...draft, acquiredAt: event.target.value });
          }}
          type="date"
          value={draft.acquiredAt}
        />
      </Field>
      <Field
        className="sm:col-span-2"
        htmlFor={`${idPrefix}-note`}
        label={t("copy.note")}
      >
        <Input
          disabled={disabled}
          id={`${idPrefix}-note`}
          maxLength={2000}
          onChange={(event) => {
            onChange({ ...draft, note: event.target.value });
          }}
          placeholder={t("copy.notePlaceholder")}
          value={draft.note}
        />
      </Field>
    </div>
  );
}