import { useCallback, useState } from "react";

import {
  readCoverSize,
  readViewMode,
  saveCoverSize,
  saveViewMode,
  type CoverSize,
  type ViewMode,
} from "./library-view-state";

export interface LibraryViewPreferences {
  coverSize: CoverSize;
  defaultView: ViewMode;
  saveView: (view: ViewMode) => void;
  setCoverSize: (size: CoverSize) => void;
}

export function useLibraryViewPreferences(): LibraryViewPreferences {
  const [coverSize, setCoverSizeState] = useState<CoverSize>(readCoverSize);
  const [defaultView, setDefaultView] = useState<ViewMode>(readViewMode);

  const setCoverSize = useCallback((size: CoverSize) => {
    setCoverSizeState(size);
    saveCoverSize(size);
  }, []);

  const saveView = useCallback((view: ViewMode) => {
    setDefaultView(view);
    saveViewMode(view);
  }, []);

  return { coverSize, defaultView, saveView, setCoverSize };
}
