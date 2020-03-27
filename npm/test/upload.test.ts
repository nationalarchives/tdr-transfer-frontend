import * as clientprocessing from "../src/clientprocessing"
import { upload, retrieveConsignmentId } from "../src/upload"
import { GraphqlClient } from "../src/graphql"

beforeEach(() => jest.resetModules())

test("Calls processing files function if file upload form present", () => {
  const spy = jest.spyOn(clientprocessing, "processFiles")
  const client = new GraphqlClient("test", "token")
  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  upload(client)
  expect(spy).toBeCalledTimes(1)

  spy.mockRestore()
})

test("Does not call processing files if file upload form is not present", () => {
  const spy = jest.spyOn(clientprocessing, "processFiles")
  const client = new GraphqlClient("test", "token")

  document.body.innerHTML =
    "<div>" +
    '<form id="wrong-id">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  upload(client)
  expect(spy).toBeCalledTimes(0)

  spy.mockRestore()
})

test("Return consignment id from windows location", () => {
  const url = "https://test.gov.uk/consignment/1/upload"
  Object.defineProperty(window, "location", {
    value: {
      href: url,
      pathname: "/consignment/1/upload"
    }
  })

  expect(retrieveConsignmentId()).toBe(1)
})
