import { IFileWithPath } from "@nationalarchives/file-information"

export enum EntryKind {
  File = "file",
  Directory = "directory"
}

export interface IFileEntry extends IFileWithPath {
  unreadable?: boolean
  kind: EntryKind.File
}

export type IEntryWithPath = IFileEntry | IDirectoryWithPath
export interface IDirectoryWithPath {
  path: string
  unreadable?: boolean
  kind: EntryKind.Directory
}

export function isDirectory(
  entry: IEntryWithPath
): entry is IDirectoryWithPath {
  return entry.kind === EntryKind.Directory
}

export function isFile(entry: IEntryWithPath): entry is IFileEntry {
  return entry.kind === EntryKind.File
}

export function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  msg: string
): Promise<T> {
  let timer: ReturnType<typeof setTimeout>
  const timeout = new Promise<T>((_, reject) => {
    timer = setTimeout(() => reject(new Error(msg)), ms)
  })
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer))
}
