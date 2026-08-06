import { IEntry, withTimeout, EntryKind } from "./file-types"

const READ_ENTRIES_TIMEOUT_MS = 5000
const GET_FILE_MAX_RETRIES = 3
const GET_FILE_RETRY_DELAY_MS = 500

const delay = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms))
const MAX_READ_ENTRIES_BATCHES = 10000
type WebkitRawEntry = NonNullable<
  ReturnType<DataTransferItem["webkitGetAsEntry"]>
>

export const getAllFiles: (
  entry: IWebkitEntry | null,
  fileInfoInput: IEntry[]
) => Promise<IEntry[]> = async (entry, fileInfoInput) => {
  if (!entry) {
    return fileInfoInput
  }

  let entries: IWebkitEntry[] | null
  try {
    const reader: IReader = entry.createReader()
    entries = await getEntriesFromReader(reader, entry.fullPath)
  } catch {
    fileInfoInput.push({
      path: entry.fullPath,
      unreadable: true,
      kind: EntryKind.Directory
    })
    return fileInfoInput
  }

  if (entries === null) {
    fileInfoInput.push({
      path: entry.fullPath,
      unreadable: true,
      kind: EntryKind.Directory
    })
    return fileInfoInput
  }

  if (entry.isDirectory && entries.length === 0) {
    fileInfoInput.push({ path: entry.fullPath, kind: EntryKind.Directory })
  }
  for (const entry of entries) {
    if (entry.isDirectory) {
      await getAllFiles(entry, fileInfoInput)
    } else {
      const fileEntry: IEntry | null = await getFileFromEntry(entry)
      if (fileEntry) {
        fileInfoInput.push(fileEntry)
      } else {
        fileInfoInput.push({
          path: entry.fullPath,
          unreadable: true,
          kind: EntryKind.File
        } as IEntry)
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

    for (let i = 0; i < MAX_READ_ENTRIES_BATCHES && nextBatch.length > 0; i++) {
      allEntries = allEntries.concat(nextBatch)
      nextBatch = await withTimeout(
        getEntryBatch(reader),
        READ_ENTRIES_TIMEOUT_MS,
        `readEntries timed out for: ${dirPath}`
      )
    }
    if (nextBatch.length > 0) {
      return null
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

const getFileFromEntry: (entry: IWebkitEntry) => Promise<IEntry | null> = async (
  entry
) => {
  const attempt = () =>
    withTimeout(
      new Promise<IEntry>((resolve, reject) => {
        entry.file(
          (file) =>
            resolve({
              file,
              path: entry.fullPath,
              kind: EntryKind.File
            }),
          (err) => reject(err)
        )
      }),
      READ_ENTRIES_TIMEOUT_MS,
      `entry.file() timed out for: ${entry.fullPath}`
    )

  let lastError: unknown
  for (let i = 0; i <= GET_FILE_MAX_RETRIES; i++) {
    if (i > 0) await delay(GET_FILE_RETRY_DELAY_MS)
    try {
      return await attempt()
    } catch (err) {
      lastError = err
    }
  }
  return null
}

export interface IReader {
  readEntries: (
    successCallback: (entry: IWebkitEntry[]) => void,
    errorCallback?: (err: DOMException) => void
  ) => void
}

export interface IWebkitEntry extends DataTransferItem {
  fullPath: WebkitRawEntry["fullPath"]
  name?: WebkitRawEntry["name"]
  isFile: WebkitRawEntry["isFile"]
  isDirectory: WebkitRawEntry["isDirectory"]
  createReader: () => IReader
  file: (
    success: (file: File) => void,
    error?: (err: DOMException) => void
  ) => void
}

export function isWebkitDirectoryEntry(
  entry: ReturnType<DataTransferItem["webkitGetAsEntry"]>
): entry is IWebkitEntry & WebkitRawEntry {
  return !!entry && entry.isDirectory
}
