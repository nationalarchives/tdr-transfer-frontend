import {
  IEntry,
  IDirectoryEntry,
  IFileEntry,
  isFile,
  isDirectory,
  withTimeout
} from "./file-types"

export enum FileCheckStatus {
  Ok = "ok",
  Unreadable = "unreadable",
  LongPathIssue = "long-path-issue"
}

export interface IFileCheckResult {
  path: string
  status: FileCheckStatus
  errorMessage?: string
}

const FILE_CHECK_TIMEOUT_MS = 5000

async function checkFileReadability(
  fileWithPath: IFileEntry
): Promise<IFileCheckResult> {
  const { file, path } = fileWithPath
  try {
    const buffer = await withTimeout(
      file.slice(0, 1).arrayBuffer(),
      FILE_CHECK_TIMEOUT_MS,
      `Reading file timed out: ${path}`
    )
    if (file.size > 0 && buffer.byteLength === 0) {
      return { path, status: FileCheckStatus.LongPathIssue }
    }
    return { path, status: FileCheckStatus.Ok }
  } catch {
    return {
      path,
      status: FileCheckStatus.LongPathIssue,
      errorMessage: `Could not read: ${path}`
    }
  }
}

export async function checkFilesForLongPathIssues(
  files: IEntry[]
): Promise<IFileCheckResult[]> {
  const results: IFileCheckResult[] = []
  for (const entry of files) {
    if (isFile(entry)) {
      if (entry.unreadable) {
        results.push({
          path: entry.path,
          status: FileCheckStatus.Unreadable,
          errorMessage: `Could not read: ${entry.path}`
        })
      } else {
        const result = await checkFileReadability(entry)
        results.push(result)
      }
    } else if (isDirectory(entry)) {
      const dir = entry as IDirectoryEntry
      if (dir.unreadable) {
        results.push({
          path: dir.path,
          status: FileCheckStatus.Unreadable,
          errorMessage: `Could not read folder: ${dir.path}`
        })
      } else {
        results.push({ path: dir.path, status: FileCheckStatus.Ok })
      }
    }
  }
  return results
}

export function hasLongPathIssues(results: IFileCheckResult[]): boolean {
  return results.some((r) => r.status !== FileCheckStatus.Ok)
}

export function isWindowsOS(): boolean {
  return navigator.userAgent.includes("Windows")
}
