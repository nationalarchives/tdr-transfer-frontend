import { IFileWithPath } from "@nationalarchives/file-information"

export type IEntryWithPath = IFileWithPath | IDirectoryWithPath
export interface IDirectoryWithPath {
  path: string
  unreadable?: boolean
}

export function isFile(entry: IEntryWithPath): entry is IFileWithPath {
  return (entry as IFileWithPath).file !== undefined
}

export function isDirectory(
  entry: IEntryWithPath
): entry is IDirectoryWithPath {
  return !isFile(entry)
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
