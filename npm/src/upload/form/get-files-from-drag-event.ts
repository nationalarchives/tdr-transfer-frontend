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

const READ_ENTRIES_TIMEOUT_MS = 5000

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

export const getAllFiles: (
  entry: IWebkitEntry | null,
  fileInfoInput: IEntryWithPath[]
) => Promise<IEntryWithPath[]> = async (entry, fileInfoInput) => {
  if (!entry) {
    return fileInfoInput
  }

  let entries: IWebkitEntry[] | null
  try {
    const reader: IReader = entry.createReader()
    entries = await getEntriesFromReader(reader, entry.fullPath)
  } catch {
    fileInfoInput.push({ path: entry.fullPath, unreadable: true })
    return fileInfoInput
  }

  if (entries === null) {
    fileInfoInput.push({ path: entry.fullPath, unreadable: true })
    return fileInfoInput
  }

  if (entry.isDirectory && entries.length === 0) {
    fileInfoInput.push({ path: entry.fullPath })
  }
  for (const entry of entries) {
    if (entry.isDirectory) {
      await getAllFiles(entry, fileInfoInput)
    } else {
      const file: IFileWithPath | null = await getFileFromEntry(entry)
      if (file) {
        fileInfoInput.push(file)
      } else {
        fileInfoInput.push({ path: entry.fullPath, unreadable: true })
      }
    }
  }
  return fileInfoInput
}

const getEntriesFromReader: (
  reader: IReader,
  dirPath: string
) => Promise<IWebkitEntry[] | null> = async (reader, dirPath) => {
  let allEntries: IWebkitEntry[] = []
  try {
    let nextBatch = await withTimeout(
      getEntryBatch(reader),
      READ_ENTRIES_TIMEOUT_MS,
      `readEntries timed out for: ${dirPath}`
    )

    while (nextBatch.length > 0) {
      allEntries = allEntries.concat(nextBatch)
      nextBatch = await withTimeout(
        getEntryBatch(reader),
        READ_ENTRIES_TIMEOUT_MS,
        `readEntries timed out for: ${dirPath}`
      )
    }
  } catch {
    return null
  }

  return allEntries
}

const getEntryBatch: (reader: IReader) => Promise<IWebkitEntry[]> = (
  reader
) => {
  return new Promise<IWebkitEntry[]>((resolve, reject) => {
    reader.readEntries(
      (entries) => resolve(entries),
      (err) => reject(err)
    )
  })
}

const getFileFromEntry: (
  entry: IWebkitEntry
) => Promise<IFileWithPath | null> = (entry) => {
  return withTimeout(
    new Promise<IFileWithPath>((resolve, reject) => {
      entry.file(
        (file) =>
          resolve({
            file,
            path: entry.fullPath
          }),
        (err) => reject(err)
      )
    }),
    READ_ENTRIES_TIMEOUT_MS,
    `entry.file() timed out for: ${entry.fullPath}`
  ).catch((): null => null)
}

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
      const children = await getAllFilesFromHandle(
        handle,
        fullPath
      ).catch((): null => null)
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

export interface IReader {
  readEntries: (
    successCallback: (entry: IWebkitEntry[]) => void,
    errorCallback?: (err: DOMException) => void
  ) => void
}

export interface IWebkitEntry extends DataTransferItem {
  createReader: () => IReader
  isFile: boolean
  isDirectory: boolean
  fullPath: string
  name?: string
  file: (
    success: (file: File) => void,
    error?: (err: DOMException) => void
  ) => void
}
