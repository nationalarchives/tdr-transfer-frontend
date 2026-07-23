import { IFileWithPath as IFileWithPathBase } from "@nationalarchives/file-information"

export enum EntryKind {
  File = "file",
  Directory = "directory"
}

export interface IFileWithPath extends IFileWithPathBase {
  kind: EntryKind.File
}

export type IEntryWithPath = IFileWithPath | IDirectoryWithPath
export interface IDirectoryWithPath {
  path: string
  unreadable?: boolean
  kind: EntryKind.Directory
}

export function isDirectory(
  entry: IEntryWithPath
): entry is IDirectoryWithPath {
  return "kind" in entry && entry.kind === EntryKind.Directory
}

export function isFile(entry: IEntryWithPath): entry is IFileWithPath {
  return "kind" in entry && entry.kind === EntryKind.File
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
