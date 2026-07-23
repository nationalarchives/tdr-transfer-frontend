import {
  getAllFilesFromHandle,
  supportsDirectoryPicker,
  IFileSystemDirectoryHandle,
  IFileSystemFileHandle
} from "../src/upload/form/get-files-from-directory-picker"

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
      const iterator: AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > = {
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
      }
      return iterator
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
      const iterator: AsyncIterableIterator<
        [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
      > = {
        [Symbol.asyncIterator]() {
          return this
        },
        next() {
          return Promise.reject(new Error("Permission denied"))
        }
      }
      return iterator
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
        const iterator: AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        > = {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        }
        return iterator
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([
      { path: "/root/bad-folder", unreadable: true, kind: "directory" }
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
        const iterator: AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        > = {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        }
        return iterator
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({
      file: testFile,
      path: "/root/test.txt",
      kind: "file"
    })
    expect(result[1]).toEqual({
      path: "/root/bad-folder",
      unreadable: true,
      kind: "directory"
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
        const iterator: AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        > = {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        }
        return iterator
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
        const iterator: AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        > = {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        }
        return iterator
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([
      { path: "/root/middle/deep-bad", unreadable: true, kind: "directory" }
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
        const iterator: AsyncIterableIterator<
          [string, IFileSystemFileHandle | IFileSystemDirectoryHandle]
        > = {
          [Symbol.asyncIterator]() {
            return this
          },
          next() {
            if (index < items.length) {
              return Promise.resolve({ value: items[index++], done: false })
            }
            return Promise.resolve({ value: undefined, done: true })
          }
        }
        return iterator
      }
    }

    const result = await getAllFilesFromHandle(rootHandle, "/root")

    expect(result).toEqual([{ path: "/root/empty", kind: "directory" }])
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
