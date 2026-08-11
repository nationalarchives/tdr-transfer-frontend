import {
  getAllFilesFromHandle,
  supportsDirectoryPicker,
  IFileSystemDirectoryHandle,
  IFileSystemFileHandle
} from "../src/upload/form/get-files-from-directory-picker"
import { EntryKind } from "../src/upload/form/file-types"

function createMockFileHandle(name: string, file: File): IFileSystemFileHandle {
  return {
    kind: "file" as const,
    name,
    getFile: () => Promise.resolve(file)
  }
}

function createMockDirectoryHandle(
  name: string,
  children: [string, IFileSystemFileHandle | IFileSystemDirectoryHandle][]
): IFileSystemDirectoryHandle {
  return {
    kind: "directory" as const,
    name,
    entries: (): AsyncIterableIterator<
      [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
    > => {
      let index = 0
      return {
        [Symbol.asyncIterator]() {
          return this
        },
        next() {
          if (index < children.length) {
            return Promise.resolve({
              value: children[index++],
              done: false
            })
          }
          return Promise.resolve({ value: undefined, done: true })
        }
      } as AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      >
    }
  }
}

function createUnreadableDirectoryHandle(
  name: string
): IFileSystemDirectoryHandle {
  return {
    kind: "directory" as const,
    name,
    entries: (): AsyncIterableIterator<
      [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
    > => {
      return {
        [Symbol.asyncIterator]() {
          return this
        },
        next() {
          return Promise.reject(new Error("Permission denied"))
        }
      } as AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      >
    }
  }
}

describe("getAllFilesFromHandle", () => {
  it("should mark an unreadable subdirectory as unreadable", async () => {
    const unreadableSubDir = createUnreadableDirectoryHandle("bad-folder")

    const rootHandle: IFileSystemDirectoryHandle = {
      kind: "directory" as const,
      name: "root",
      entries: (): AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > => {
        const items: [
          string,
          IFileSystemFileHandle | IFileSystemDirectoryHandle
        ][] = [["bad-folder", unreadableSubDir]]
        let index = 0
        return {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        } as AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        >
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([
      { path: "/root/bad-folder", unreadable: true, kind: EntryKind.Directory }
    ])
  })

  it("should mark files alongside unreadable subdirectories correctly", async () => {
    const testFile = new File(["hello"], "test.txt")
    const fileHandle = createMockFileHandle("test.txt", testFile)
    const unreadableSubDir = createUnreadableDirectoryHandle("bad-folder")

    const rootHandle: IFileSystemDirectoryHandle = {
      kind: "directory" as const,
      name: "root",
      entries: (): AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > => {
        const items: [
          string,
          IFileSystemFileHandle | IFileSystemDirectoryHandle
        ][] = [
          ["test.txt", fileHandle],
          ["bad-folder", unreadableSubDir]
        ]
        let index = 0
        return {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        } as AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        >
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({
      file: testFile,
      path: "/root/test.txt",
      kind: EntryKind.File
    })
    expect(result[1]).toEqual({
      path: "/root/bad-folder",
      unreadable: true,
      kind: EntryKind.Directory
    })
  })

  it("should mark a nested unreadable subdirectory as unreadable", async () => {
    const unreadableSubDir = createUnreadableDirectoryHandle("deep-bad")

    const middleDir: IFileSystemDirectoryHandle = {
      kind: "directory" as const,
      name: "middle",
      entries: (): AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > => {
        const items: [
          string,
          IFileSystemFileHandle | IFileSystemDirectoryHandle
        ][] = [["deep-bad", unreadableSubDir]]
        let index = 0
        return {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        } as AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        >
      }
    }

    const rootHandle: IFileSystemDirectoryHandle = {
      kind: "directory" as const,
      name: "root",
      entries: (): AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > => {
        const items: [
          string,
          IFileSystemFileHandle | IFileSystemDirectoryHandle
        ][] = [["middle", middleDir]]
        let index = 0
        return {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        } as AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        >
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([
      {
        path: "/root/middle/deep-bad",
        unreadable: true,
        kind: EntryKind.Directory
      }
    ])
  })

  it("should throw when the root directory itself is unreadable", async () => {
    const rootHandle = createUnreadableDirectoryHandle("root")

    await expect(getAllFilesFromHandle(rootHandle, "/root")).rejects.toThrow(
      "Permission denied"
    )
  })

  it("should record an empty subdirectory as a directory entry", async () => {
    const emptyDir = createMockDirectoryHandle("empty", [])

    const rootHandle: IFileSystemDirectoryHandle = {
      kind: "directory" as const,
      name: "root",
      entries: (): AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > => {
        const items: [
          string,
          IFileSystemFileHandle | IFileSystemDirectoryHandle
        ][] = [["empty", emptyDir]]
        let index = 0
        return {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        } as AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        >
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([{ path: "/root/empty", kind: EntryKind.Directory }])
  })

  it("should retry file reads for transient errors", async () => {
    const testFile = new File(["hello"], "test.txt")
    const transientError = new Error("Temporary read failure")
    transientError.name = "NotReadableError"

    const getFile = jest
      .fn()
      .mockRejectedValueOnce(transientError)
      .mockResolvedValueOnce(testFile)

    const fileHandle: IFileSystemFileHandle = {
      kind: "file",
      name: "test.txt",
      getFile
    }

    const rootHandle = createMockDirectoryHandle("root", [
      ["test.txt", fileHandle]
    ])
    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(getFile).toHaveBeenCalledTimes(2)
    expect(result).toEqual([
      { file: testFile, path: "/root/test.txt", kind: EntryKind.File }
    ])
  })

  it("should not retry file reads for non-transient errors", async () => {
    const getFile = jest.fn().mockRejectedValue(new Error("Permission denied"))

    const fileHandle: IFileSystemFileHandle = {
      kind: "file",
      name: "test.txt",
      getFile
    }

    const rootHandle = createMockDirectoryHandle("root", [
      ["test.txt", fileHandle]
    ])
    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(getFile).toHaveBeenCalledTimes(1)
    expect(result).toEqual([
      { path: "/root/test.txt", unreadable: true, kind: EntryKind.File }
    ])
  })
})

describe("supportsDirectoryPicker", () => {
  it("should return true when showDirectoryPicker exists on window", () => {
    ;(window as any).showDirectoryPicker = jest.fn()
    expect(supportsDirectoryPicker()).toBe(true)
    delete (window as any).showDirectoryPicker
  })

  it("should return false when showDirectoryPicker does not exist", () => {
    delete (window as any).showDirectoryPicker
    expect(supportsDirectoryPicker()).toBe(false)
  })
})
