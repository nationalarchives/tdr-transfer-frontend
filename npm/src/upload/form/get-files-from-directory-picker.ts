import { IEntryWithPath, withTimeout, EntryKind } from "./file-types"

const READ_ENTRIES_TIMEOUT_MS = 5000

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
): Promise<IEntryWithPath[]> {
  const fileInfos: IEntryWithPath[] = []
  for await (const [name, handle] of dirHandle.entries()) {
    const fullPath = pathPrefix + "/" + name
    if (handle.kind === "directory") {
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
  fileInfos: IEntryWithPath[]
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
  fileInfos: IEntryWithPath[]
): Promise<void> {
  try {
    const file = await withTimeout(
      handle.getFile(),
      READ_ENTRIES_TIMEOUT_MS,
      `getFile timed out for: ${fullPath}`
    )
    fileInfos.push({ file, path: fullPath, kind: EntryKind.File })
  } catch {
    fileInfos.push({
      path: fullPath,
      unreadable: true,
      kind: EntryKind.Directory
    })
  }
}

export function supportsDirectoryPicker(): boolean {
  return "showDirectoryPicker" in window
}
