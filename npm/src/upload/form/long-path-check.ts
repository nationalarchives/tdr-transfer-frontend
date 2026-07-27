import {
  IEntryWithPath,
  IDirectoryWithPath,
  IFileEntry,
  isFile,
  isDirectory,
  withTimeout
} from "./file-types"

export interface IFileCheckResult {
  path: string
  status: "ok" | "unreadable" | "long-path-issue"
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
      return { path, status: "long-path-issue" }
    }
    return { path, status: "ok" }
  } catch {
    return {
      path,
      status: "long-path-issue",
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
      if (entry.unreadable) {
        results.push({
          path: entry.path,
          status: "long-path-issue",
          errorMessage: `Could not read: ${entry.path}`
        })
      } else {
        const result = await checkFileReadability(entry)
        results.push(result)
      }
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
