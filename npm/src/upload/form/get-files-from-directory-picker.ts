import { IEntryWithPath, withTimeout } from "./file-types"

const READ_ENTRIES_TIMEOUT_MS = 5000

interface IFileSystemFileHandle {
  kind: "file"
  name: string
  getFile: () => Promise<File>
}

interface IFileSystemDirectoryHandle {
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
      const children = await getAllFilesFromHandle(handle, fullPath).catch(
        (): null => null
      )
      if (children === null) {
        fileInfos.push({ path: fullPath, unreadable: true })
      } else if (children.length === 0) {
        fileInfos.push({ path: fullPath })
      } else {
        fileInfos.push(...children)
      }
    } else {
      try {
        const file = await withTimeout(
          handle.getFile(),
          READ_ENTRIES_TIMEOUT_MS,
          `getFile timed out for: ${fullPath}`
        )
        fileInfos.push({ file, path: fullPath })
      } catch {
        fileInfos.push({ path: fullPath, unreadable: true })
      }
    }
  }
  return fileInfos
}

export function supportsDirectoryPicker(): boolean {
  return "showDirectoryPicker" in window
}
