beforeEach(() => jest.resetModules())

test("upload console logs error when upload fails", () => {
  const consoleSpy = jest.spyOn(console, "error").mockImplementation(() => {})
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form" data-consignment-id="12345">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  if (uploadForm) {
    uploadForm.submit()
  }

  expect(consoleSpy).toHaveBeenCalled()
})

test("upload console logs error no consignment id provided", () => {
  const consoleSpy = jest.spyOn(console, "error").mockImplementation(() => {})
  document.body.innerHTML =
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  const uploadForm: HTMLFormElement | null = document.querySelector(
    "#file-upload-form"
  )

  if (uploadForm) {
    uploadForm.submit()
  }

  expect(consoleSpy).toHaveBeenCalled()
})
