import { InjectionToken } from '@angular/core';

export type TextFileDownloader = typeof downloadTextFile;

export const DOWNLOAD_TEXT_FILE = new InjectionToken<TextFileDownloader>('DOWNLOAD_TEXT_FILE', {
  providedIn: 'root',
  factory: () => downloadTextFile,
});

export function downloadTextFile(content: string, fileName: string, mimeType: string): void {
  const blob = new Blob([content], { type: mimeType });
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileName;
  anchor.hidden = true;
  document.body.append(anchor);

  try {
    anchor.click();
  } finally {
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  }
}
