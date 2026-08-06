import {
  IEntry,
  withTimeout,
  EntryKind,
  isTransientFileReadError
} from "./file-types"

const READ_ENTRIES_TIMEOUT_MS = 5000
const GET_FILE_MAX_RETRIES = 3
const GET_FILE_RETRY_DELAY_MS = 500

const delay = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms))

export interface IFileSystemFileHandle {
  kind: "file"
  name: string
  getFile: () => Promise<File>
}

export interface IFileSystemDirectoryHandle {
  kind: "directory"
  name: string
  entries: () => AsyncIterableIterator<[string, IFileSystemHandle]>
}

type IFileSystemHandle = IFileSystemFileHandle | IFileSystemDirectoryHandle

export async function getAllFilesFromHandle(
  dirHandle: IFileSystemDirectoryHandle,
  pathPrefix: string
): Promise<IEntry[]> {
  const fileInfos: IEntry[] = []
  for await (const [name, handle] of dirHandle.entries()) {
    const fullPath = pathPrefix + "/" + name
    if (handle.kind === EntryKind.Directory) {
      await handleDirectoryEntry(handle, fullPath, fileInfos)
    } else {
      await handleFileEntry(handle, fullPath, fileInfos)
    }
  }
  return fileInfos
}

async function handleDirectoryEntry(
  handle: IFileSystemDirectoryHandle,
  fullPath: string,
  fileInfos: IEntry[]
): Promise<void> {
  const children = await getAllFilesFromHandle(handle, fullPath).catch(
    (): null => null
  )
  if (children === null) {
    fileInfos.push({
      path: fullPath,
      unreadable: true,
      kind: EntryKind.Directory
    })
  } else if (children.length === 0) {
    fileInfos.push({ path: fullPath, kind: EntryKind.Directory })
  } else {
    fileInfos.push(...children)
  }
}

async function handleFileEntry(
  handle: IFileSystemFileHandle,
  fullPath: string,
  fileInfos: IEntry[]
): Promise<void> {
  for (let attempt = 0; attempt <= GET_FILE_MAX_RETRIES; attempt++) {
    if (attempt > 0) await delay(GET_FILE_RETRY_DELAY_MS)
    try {
      const file = await withTimeout(
        handle.getFile(),
        READ_ENTRIES_TIMEOUT_MS,
        `getFile timed out for: ${fullPath}`
      )
      fileInfos.push({ file, path: fullPath, kind: EntryKind.File })
      return
    } catch (err) {
      if (!isTransientFileReadError(err)) {
        break
      }
    }
  }
  fileInfos.push({
    path: fullPath,
    unreadable: true,
    kind: EntryKind.File
  } as IEntry)
}

export function supportsDirectoryPicker(): boolean {
  return "showDirectoryPicker" in window
}
