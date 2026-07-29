import {
  getAllFilesFromHandle,
  supportsDirectoryPicker
} from "../src/upload/form/get-files-from-directory-picker"

function createMockFileHandle(name: string, file: File) {
  return {
    kind: "file" as const,
    name,
    getFile: () => Promise.resolve(file)
  }
}

function createMockDirectoryHandle(
  name: string,
  children: [string, { kind: string; name: string }][]
) {
  return {
    kind: "directory" as const,
    name,
    entries: () => {
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
      } as AsyncIterableIterator<[string, any]>
    }
  }
}

function createUnreadableDirectoryHandle(name: string) {
  return {
    kind: "directory" as const,
    name,
    entries: () => {
      return {
        [Symbol.asyncIterator]() {
          return this
        },
        next() {
          return Promise.reject(new Error("Permission denied"))
        }
      } as AsyncIterableIterator<[string, any]>
    }
  }
}

describe("getAllFilesFromHandle", () => {
  it("should mark an unreadable subdirectory as unreadable", async () => {
    const unreadableSubDir = createUnreadableDirectoryHandle("bad-folder")

    const rootHandle = {
      kind: "directory" as const,
      name: "root",
      entries: () => {
        const items: [string, any][] = [["bad-folder", unreadableSubDir]]
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
        } as AsyncIterableIterator<[string, any]>
      }
    }

    const result = await getAllFilesFromHandle(rootHandle as any, "/root")

    expect(result).toEqual([
      { path: "/root/bad-folder", unreadable: true }
    ])
  })

  it("should mark files alongside unreadable subdirectories correctly", async () => {
    const testFile = new File(["hello"], "test.txt")
    const fileHandle = createMockFileHandle("test.txt", testFile)
    const unreadableSubDir = createUnreadableDirectoryHandle("bad-folder")

    const rootHandle = {
      kind: "directory" as const,
      name: "root",
      entries: () => {
        const items: [string, any][] = [
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
        } as AsyncIterableIterator<[string, any]>
      }
    }

    const result = await getAllFilesFromHandle(rootHandle as any, "/root")

    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({ file: testFile, path: "/root/test.txt" })
    expect(result[1]).toEqual({ path: "/root/bad-folder", unreadable: true })
  })

  it("should mark a nested unreadable subdirectory as unreadable", async () => {
    const unreadableSubDir = createUnreadableDirectoryHandle("deep-bad")

    const middleDir = {
      kind: "directory" as const,
      name: "middle",
      entries: () => {
        const items: [string, any][] = [["deep-bad", unreadableSubDir]]
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
        } as AsyncIterableIterator<[string, any]>
      }
    }

    const rootHandle = {
      kind: "directory" as const,
      name: "root",
      entries: () => {
        const items: [string, any][] = [["middle", middleDir]]
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
        } as AsyncIterableIterator<[string, any]>
      }
    }

    const result = await getAllFilesFromHandle(rootHandle as any, "/root")

    expect(result).toEqual([
      { path: "/root/middle/deep-bad", unreadable: true }
    ])
  })

  it("should throw when the root directory itself is unreadable", async () => {
    const rootHandle = createUnreadableDirectoryHandle("root")

    await expect(
      getAllFilesFromHandle(rootHandle as any, "/root")
    ).rejects.toThrow("Permission denied")
  })

  it("should record an empty subdirectory as a directory entry", async () => {
    const emptyDir = createMockDirectoryHandle("empty", [])

    const rootHandle = {
      kind: "directory" as const,
      name: "root",
      entries: () => {
        const items: [string, any][] = [["empty", emptyDir]]
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
        } as AsyncIterableIterator<[string, any]>
      }
    }

    const result = await getAllFilesFromHandle(rootHandle as any, "/root")

    expect(result).toEqual([{ path: "/root/empty" }])
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
