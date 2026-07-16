import {
  IEntryWithPath,
  IDirectoryWithPath,
  isFile,
  isDirectory
} from "./get-files-from-drag-event"
import { IFileWithPath } from "@nationalarchives/file-information"

export interface IFileCheckResult {
  path: string
  status: "ok" | "unreadable" | "long-path-issue"
  errorMessage?: string
}

const FILE_CHECK_TIMEOUT_MS = 5000

function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  msg: string
): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => setTimeout(() => reject(new Error(msg)), ms))
  ])
}

async function checkFileReadability(
  fileWithPath: IFileWithPath
): Promise<IFileCheckResult> {
  const { file, path } = fileWithPath
  try {
    const buffer = await withTimeout(
      file.slice(0, 1).arrayBuffer(),
      FILE_CHECK_TIMEOUT_MS,
      `Reading file timed out: ${path}`
    )
    if (file.size > 0 && buffer.byteLength === 0) {
      return { path, status: "long-path-issue" }
    }
    return { path, status: "ok" }
  } catch {
    return {
      path,
      status: "unreadable",
      errorMessage: `Could not read: ${path}`
    }
  }
}

export async function checkFilesForLongPathIssues(
  files: IEntryWithPath[]
): Promise<IFileCheckResult[]> {
  const results: IFileCheckResult[] = []
  for (const entry of files) {
    if (isFile(entry)) {
      const result = await checkFileReadability(entry)
      results.push(result)
    } else if (isDirectory(entry)) {
      const dir = entry as IDirectoryWithPath
      if (dir.unreadable) {
        results.push({
          path: dir.path,
          status: "unreadable",
          errorMessage: `Could not read folder: ${dir.path}`
        })
      } else {
        results.push({ path: dir.path, status: "ok" })
      }
    }
  }
  return results
}

export function hasLongPathIssues(results: IFileCheckResult[]): boolean {
  return results.some((r) => r.status !== "ok")
}

export function isWindowsOS(): boolean {
  return navigator.userAgent.includes("Windows")
}
