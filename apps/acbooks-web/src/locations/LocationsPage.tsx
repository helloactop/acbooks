import {
  Button,
  Field,
  IconButton,
  Input,
  Select,
  buttonVariants,
} from "@acbooks/ui";
import { BookOpen, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "../layout/app-shell-context";
import { PageHeader } from "../layout/PageHeader";
import { locationBooksHref } from "../layout/routes";
import { useDocumentTitle } from "../layout/use-document-title";
import { libraryErrorMessage } from "../library/errors";
import { locationOptions } from "../library/location-options";
import {
  createLocation,
  deleteLocation,
  listLocations,
  updateLocation,
  type LocationNode,
} from "../library/locations-api";
import { Modal } from "../library/Modal";

interface FlatLocation {
  depth: number;
  hasChildren: boolean;
  node: LocationNode;
}

function flattenLocations(locations: readonly LocationNode[]): FlatLocation[] {
  const ids = new Set(locations.map((location) => location.id));
  const children = new Map<string | null, LocationNode[]>();
  for (const location of locations) {
    const parent =
      location.parentId !== null && ids.has(location.parentId)
        ? location.parentId
        : null;
    const list = children.get(parent) ?? [];
    list.push(location);
    children.set(parent, list);
  }
  for (const list of children.values()) {
    list.sort((left, right) => left.name.localeCompare(right.name));
  }
  const result: FlatLocation[] = [];
  const walk = (parent: string | null, depth: number) => {
    for (const location of children.get(parent) ?? []) {
      const subs = children.get(location.id) ?? [];
      result.push({ depth, hasChildren: subs.length > 0, node: location });
      walk(location.id, depth + 1);
    }
  };
  walk(null, 0);
  return result;
}

function descendantIds(
  locations: readonly LocationNode[],
  rootId: string,
): Set<string> {
  const children = new Map<string, string[]>();
  for (const location of locations) {
    if (location.parentId !== null) {
      const list = children.get(location.parentId) ?? [];
      list.push(location.id);
      children.set(location.parentId, list);
    }
  }
  const seen = new Set<string>();
  const stack = [rootId];
  while (stack.length > 0) {
    const current = stack.pop();
    if (current === undefined) {
      continue;
    }
    for (const child of children.get(current) ?? []) {
      if (!seen.has(child)) {
        seen.add(child);
        stack.push(child);
      }
    }
  }
  return seen;
}

export function LocationsPage() {
  const { t } = useTranslation("locations");
  const { t: tLibrary } = useTranslation("library");
  const { expireSession } = useAppShell();
  useDocumentTitle(t("title"));
  const [locations, setLocations] = useState<LocationNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [dialog, setDialog] = useState<{
    editing?: LocationNode;
    parentId: string | null;
  } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<LocationNode | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<ApiError | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listLocations(controller.signal)
      .then((nodes) => {
        setLocations(nodes);
        setError(null);
      })
      .catch((cause) => {
        if (isAbortError(cause)) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          expireSession();
          return;
        }
        setError(apiError);
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [attempt, expireSession]);

  const rows = useMemo(() => flattenLocations(locations), [locations]);
  const reload = () => {
    setLoading(true);
    setAttempt((current) => current + 1);
  };

  const confirmDelete = async () => {
    if (deleteTarget === null || deleteBusy) {
      return;
    }
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteLocation(deleteTarget.id);
      setDeleteBusy(false);
      setDeleteTarget(null);
      reload();
    } catch (cause) {
      const apiError = toApiError(cause);
      setDeleteBusy(false);
      if (apiError.status === 401) {
        expireSession();
        return;
      }
      setDeleteError(apiError);
    }
  };

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(tLibrary, error);

  return (
    <div className="@container mx-auto flex min-h-0 w-full max-w-[900px] flex-col gap-4">
      <PageHeader
        actions={
          <Button
            onClick={() => {
              setDialog({ parentId: null });
            }}
            variant="soft"
          >
            {t("addRoot")}
          </Button>
        }
        description={t("description")}
        title={t("title")}
      />

      {loading ? (
        <p className="py-8 text-center text-sm text-muted-foreground">
          {t("loading")}
        </p>
      ) : errorMessage !== undefined ? (
        <div className="flex flex-col items-center gap-3 py-10">
          <p className="text-sm text-destructive-text" role="alert">
            {errorMessage}
          </p>
          <Button onClick={reload} variant="soft">
            {t("retry")}
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <p className="rounded-card border border-border bg-surface px-6 py-10 text-center text-sm text-muted-foreground">
          {t("empty")}
        </p>
      ) : (
        <ul
          aria-label={t("title")}
          className="flex flex-col divide-y divide-border overflow-hidden rounded-card border border-border"
        >
          {rows.map(({ depth, hasChildren, node }) => (
            <li
              className="flex flex-wrap items-center gap-x-2 gap-y-1 p-3"
              key={node.id}
              style={{ paddingLeft: 12 + depth * 20 }}
            >
              <span className="min-w-0 flex-1 truncate font-medium">
                {node.name}
              </span>
              <span className="text-xs tabular-nums text-muted-foreground">
                {t("copyCount", { count: node.copyCount })}
              </span>
              <Link
                className={buttonVariants({ size: "sm", variant: "ghost" })}
                to={locationBooksHref(node.id)}
              >
                <BookOpen aria-hidden="true" className="size-4" />
                {t("viewBooks")}
              </Link>
              <IconButton
                label={t("addChild")}
                onClick={() => {
                  setDialog({ parentId: node.id });
                }}
              >
                <Plus aria-hidden="true" />
              </IconButton>
              <IconButton
                label={t("edit")}
                onClick={() => {
                  setDialog({ editing: node, parentId: node.parentId });
                }}
              >
                <Pencil aria-hidden="true" />
              </IconButton>
              {hasChildren ? null : (
                <IconButton
                  label={t("delete")}
                  onClick={() => {
                    setDeleteError(null);
                    setDeleteTarget(node);
                  }}
                >
                  <Trash2 aria-hidden="true" />
                </IconButton>
              )}
            </li>
          ))}
        </ul>
      )}

      {dialog !== null ? (
        <LocationDialog
          editing={dialog.editing}
          locations={locations}
          onClose={() => {
            setDialog(null);
          }}
          onSaved={() => {
            setDialog(null);
            reload();
          }}
          onSessionExpired={expireSession}
          parentId={dialog.parentId}
        />
      ) : null}

      {deleteTarget !== null ? (
        <Modal
          describedBy="delete-location-description"
          labelledBy="delete-location-title"
          onClose={
            deleteBusy
              ? () => undefined
              : () => {
                  setDeleteTarget(null);
                }
          }
          size="confirm"
        >
          <div className="p-5">
            <h2 className="text-lg font-semibold" id="delete-location-title">
              {t("deleteTitle", { name: deleteTarget.name })}
            </h2>
            <p
              className="mt-2 text-sm text-muted-foreground"
              id="delete-location-description"
            >
              {t("deleteDescription")}
            </p>
            {deleteError !== null ? (
              <p className="mt-3 text-sm text-destructive-text" role="alert">
                {libraryErrorMessage(tLibrary, deleteError)}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button
                disabled={deleteBusy}
                onClick={() => {
                  setDeleteTarget(null);
                }}
                variant="ghost"
              >
                {t("cancel")}
              </Button>
              <Button
                disabled={deleteBusy}
                onClick={() => {
                  void confirmDelete();
                }}
                variant="danger-confirm"
              >
                {t("deleteConfirm")}
              </Button>
            </div>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}

interface LocationDialogProps {
  editing?: LocationNode;
  locations: readonly LocationNode[];
  onClose: () => void;
  onSaved: () => void;
  onSessionExpired: () => void;
  parentId: string | null;
}

function LocationDialog({
  editing,
  locations,
  onClose,
  onSaved,
  onSessionExpired,
  parentId,
}: LocationDialogProps) {
  const { t } = useTranslation("locations");
  const { t: tLibrary } = useTranslation("library");
  const [name, setName] = useState(editing?.name ?? "");
  const [parent, setParent] = useState(parentId ?? "");
  const [nameError, setNameError] = useState<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const excluded = editing
    ? new Set([editing.id, ...descendantIds(locations, editing.id)])
    : new Set<string>();
  const options = [
    { label: t("parentNone"), value: "" },
    ...locationOptions(
      locations.filter((location) => !excluded.has(location.id)),
    ),
  ];

  const submit = async () => {
    if (busy) {
      return;
    }
    const trimmed = name.trim();
    if (trimmed.length === 0) {
      setNameError(t("nameRequired"));
      return;
    }
    setNameError(undefined);
    setBusy(true);
    setError(null);
    try {
      const nextParent = parent.length > 0 ? parent : null;
      if (editing !== undefined) {
        await updateLocation(editing.id, {
          name: trimmed,
          parentId: nextParent,
        });
      } else {
        await createLocation({ name: trimmed, parentId: nextParent });
      }
      setBusy(false);
      onSaved();
    } catch (cause) {
      const apiError = toApiError(cause);
      if (apiError.status === 401) {
        onSessionExpired();
        return;
      }
      setError(apiError);
      setBusy(false);
    }
  };

  const errorMessage =
    error === null ? undefined : libraryErrorMessage(tLibrary, error);

  return (
    <Modal
      labelledBy="location-form-title"
      onClose={busy ? () => undefined : onClose}
    >
      <div className="flex items-center justify-between gap-3 border-b border-border p-4 sm:p-5">
        <h2 className="text-lg font-semibold" id="location-form-title">
          {editing === undefined ? t("formCreateTitle") : t("formEditTitle")}
        </h2>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 sm:p-5">
        {errorMessage !== undefined ? (
          <p className="text-sm text-destructive-text" role="alert">
            {errorMessage}
          </p>
        ) : null}
        <Field error={nameError} htmlFor="location-name" label={t("nameLabel")}>
          <Input
            autoComplete="off"
            disabled={busy}
            id="location-name"
            maxLength={200}
            onChange={(event) => {
              setName(event.target.value);
              setNameError(undefined);
              setError(null);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                void submit();
              }
            }}
            placeholder={t("namePlaceholder")}
            required
            value={name}
          />
        </Field>
        <Field htmlFor="location-parent" label={t("parentLabel")}>
          <Select
            ariaLabel={t("parentLabel")}
            disabled={busy}
            id="location-parent"
            onValueChange={(value) => {
              setParent(value);
              setError(null);
            }}
            options={options}
            value={parent}
          />
        </Field>
      </div>
      <div className="flex flex-wrap justify-end gap-2 border-t border-border p-4 sm:p-5">
        <Button disabled={busy} onClick={onClose} variant="ghost">
          {t("cancel")}
        </Button>
        <Button
          loading={busy}
          onClick={() => {
            void submit();
          }}
        >
          {t("save")}
        </Button>
      </div>
    </Modal>
  );
}