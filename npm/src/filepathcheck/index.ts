import {
  checkFilesForLongPathIssues,
  hasLongPathIssues,
  IFileCheckResult
} from "../upload/form/long-path-check"
import {
  getAllFiles,
  IEntryWithPath,
  isFile,
  IWebkitEntry
} from "../upload/form/get-files-from-drag-event"

export function initialiseFilePathCheck(): void {
  const dropzone = document.getElementById("file-path-check-dropzone")
  const fileInput = document.getElementById(
    "file-path-check-file-selection"
  ) as HTMLInputElement | null

  if (!dropzone || !fileInput) return

  dropzone.addEventListener("dragover", (e) => {
    e.preventDefault()
    dropzone.classList.add("drag-and-drop__dropzone--dragover")
  })

  dropzone.addEventListener("dragleave", () => {
    dropzone.classList.remove("drag-and-drop__dropzone--dragover")
  })

  dropzone.addEventListener("drop", async (e) => {
    e.preventDefault()
    dropzone.classList.remove("drag-and-drop__dropzone--dragover")
    const items = (e as DragEvent).dataTransfer?.items
    if (!items || items.length === 0) return

    if (items.length > 1) {
      showStatus("Please drop a single top-level folder.")
      return
    }

    const entry = items[0].webkitGetAsEntry()
    if (!entry || !entry.isDirectory) {
      showStatus("Please drop a folder, not a file.")
      return
    }

    showStatus("Scanning folder — this may take a moment…")
    const files = await getAllFiles(entry as unknown as IWebkitEntry, [])
    showStatus(`Checking ${files.length} item(s)…`)
    await processAndDisplayResults(files)
  })

  fileInput.addEventListener("change", async () => {
    const fileList = fileInput.files
    if (!fileList || fileList.length === 0) return

    interface FileWithRelativePath extends File {
      webkitRelativePath: string
    }

    const files: IEntryWithPath[] = [...fileList].map((file) => ({
      file,
      path: (file as FileWithRelativePath).webkitRelativePath
    }))

    showStatus(`Checking ${files.length} item(s)…`)
    await processAndDisplayResults(files)
  })
}

function showStatus(message: string): void {
  const statusEl = document.getElementById("file-path-check-status")
  const messageEl = document.getElementById("file-path-check-status-message")
  if (statusEl && messageEl) {
    messageEl.textContent = message
    statusEl.removeAttribute("hidden")
  }
}

function hideStatus(): void {
  const statusEl = document.getElementById("file-path-check-status")
  if (statusEl) {
    statusEl.setAttribute("hidden", "true")
  }
}

async function processAndDisplayResults(
  files: IEntryWithPath[]
): Promise<void> {
  const results = await checkFilesForLongPathIssues(files)
  hideStatus()

  const resultsSection = document.getElementById("file-path-check-results")
  const tableBody = document.getElementById("file-path-check-table-body")
  const table = document.getElementById("file-path-check-table")
  const summaryPanel = document.getElementById("file-path-check-summary")
  const successBanner = document.getElementById("file-path-check-success")
  const failureBanner = document.getElementById("file-path-check-failure")
  const failureMessage = document.getElementById(
    "file-path-check-failure-message"
  )
  const continueButton = document.getElementById("file-path-check-continue")

  if (!resultsSection || !tableBody || !table) return

  tableBody.innerHTML = ""
  resultsSection.removeAttribute("hidden")

  const issueResults = results.filter((r) => r.status !== "ok")
  const issueCount = issueResults.length
  const okCount = results.length - issueCount

  for (const result of issueResults) {
    const row = document.createElement("tr")
    row.className = "govuk-table__row"

    const tagClass =
      result.status === "unreadable" || result.status === "access-error"
        ? "govuk-tag--red"
        : "govuk-tag--yellow"

    const statusText =
      result.status === "unreadable"
        ? "Unreadable"
        : result.status === "access-error"
          ? "Access denied"
          : "Long path issue"

    row.innerHTML =
      `<td class="govuk-table__cell file-path-check__cell-path" title="${escapeHtml(result.path)}">${escapeHtml(result.path)}</td>` +
      `<td class="govuk-table__cell govuk-table__cell--numeric">${result.path.length}</td>` +
      `<td class="govuk-table__cell"><strong class="govuk-tag ${tagClass}">${statusText}</strong></td>`

    tableBody.appendChild(row)
  }

  if (summaryPanel) {
    const totalEl = document.getElementById("summary-total")
    const longestEl = document.getElementById("summary-longest")
    const issuesEl = document.getElementById("summary-issues")

    if (totalEl) totalEl.textContent = results.length.toString()
    if (longestEl) {
      const longest = Math.max(...results.map((r) => r.path.length))
      longestEl.textContent = `${longest} characters`
    }
    if (issuesEl) issuesEl.textContent = issueCount.toString()
    summaryPanel.removeAttribute("hidden")
  }

  if (issueCount === 0) {
    successBanner?.removeAttribute("hidden")
    failureBanner?.setAttribute("hidden", "true")
    table.setAttribute("hidden", "true")
    continueButton?.removeAttribute("hidden")
  } else {
    successBanner?.setAttribute("hidden", "true")
    failureBanner?.removeAttribute("hidden")
    table.removeAttribute("hidden")
    continueButton?.setAttribute("hidden", "true")
    if (failureMessage) {
      failureMessage.textContent = `${issueCount} of ${results.length} item(s) have long path issues. ${okCount} item(s) are OK. Please fix the issues listed below and check again.`
    }
  }
}

function escapeHtml(str: string): string {
  const div = document.createElement("div")
  div.textContent = str || ""
  return div.innerHTML
}
