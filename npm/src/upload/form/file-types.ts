import { IFileWithPath } from "@nationalarchives/file-information"

export enum EntryKind {
  File = "file",
  Directory = "directory"
}

export interface IFileEntry extends IFileWithPath {
  unreadable?: boolean
  kind: EntryKind.File
}

export type IEntry = IFileEntry | IDirectoryEntry
export interface IDirectoryEntry {
  path: string
  unreadable?: boolean
  kind: EntryKind.Directory
}

export function isDirectory(entry: IEntry): entry is IDirectoryEntry {
  return entry.kind === EntryKind.Directory
}

export function isFile(entry: IEntry): entry is IFileEntry {
  return entry.kind === EntryKind.File
}

export function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  msg: string
): Promise<T> {
  let timer: ReturnType<typeof setTimeout>
  const timeout = new Promise<T>((_, reject) => {
    timer = setTimeout(() => {
      const timeoutError = new Error(msg)
      timeoutError.name = "TimeoutError"
      reject(timeoutError)
    }, ms)
  })
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer))
}

const transientReadErrorNames = new Set([
  "TimeoutError",
  "AbortError",
  "NotReadableError",
  "UnknownError"
])

export function isTransientFileReadError(err: unknown): boolean {
  return err instanceof Error && transientReadErrorNames.has(err.name)
}
