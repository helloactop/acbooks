/**
 * Floating menus rendered through a portal must stay inside an open native
 * dialog, otherwise the dialog's top layer covers them.
 */
export function resolveOverlayContainer(
  anchor: HTMLElement | null,
): HTMLElement {
  if (anchor === null) {
    return document.body;
  }
  return anchor.closest<HTMLElement>("dialog[open]") ?? document.body;
}
