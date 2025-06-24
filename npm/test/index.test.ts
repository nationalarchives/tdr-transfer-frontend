import Keycloak from "keycloak-js"
import { renderModules } from "../src/index"

jest.mock("../src/auth")
jest.mock('uuid', () => 'eb7b7961-395d-4b4c-afc6-9ebcadaf0150')
import { getKeycloakInstance } from "../src/auth"
import { createMockKeycloakInstance } from "./utils"

const mockKeycloak: Keycloak = createMockKeycloakInstance()

const getFrontEndInfoHtml: () => string = () => {
  return `
    <input type="hidden" class="api-url">
    <input type="hidden" class="stage">
    <input type="hidden" class="region">
    <input type="hidden" class="upload-url">
    <input type="hidden" class="auth-url">
    <input type="hidden" class="client-id">
    <input type="hidden" class="realm">
  `.toString()
}

test("renderModules calls authorisation when upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    '<div id="file-upload">' +
    '<div class="govuk-file-upload">' +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>" +
    "</div>"

  await renderModules()
  expect(keycloakInstance).toHaveBeenCalledTimes(1)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when no upload form present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(keycloakInstance).toHaveBeenCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when no identity pool id present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    getFrontEndInfoHtml() +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()

  expect(keycloakInstance).toHaveBeenCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when the front end info is missing", () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    "<div>" +
    '<form id="file-upload-form">' +
    '<button class="govuk-button" type="submit" data-module="govuk-button" role="button" />' +
    "</form>" +
    "</div>"

  renderModules()
  expect(keycloakInstance).toHaveBeenCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules calls authorisation when dialog is present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML = '<a href="#" class="timeout-dialog">' + getFrontEndInfoHtml()

  await renderModules()
  expect(keycloakInstance).toHaveBeenCalledTimes(1)

  keycloakInstance.mockRestore()
})

test("renderModules does not call authorisation when dialog box is not present on page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML = '<a href="#" class="not-timeout-dialog">' + getFrontEndInfoHtml()

  await renderModules()

  expect(keycloakInstance).toHaveBeenCalledTimes(0)

  keycloakInstance.mockRestore()
})

test("renderModules should initialise the multi-select search module if the tna-multi-select-search element is present on the page", async () => {
  const keycloakInstance = getKeycloakInstance as jest.Mock
  keycloakInstance.mockImplementation(() => Promise.resolve(mockKeycloak))

  document.body.innerHTML =
    `
    <div class="tna-multi-select-search" data-module="multi-select-search">
        <div class="tna-multi-select-search" data-module="multi-select-search">
            <div class="tna-multi-select-search__filter">
                <label for="input-filter" class="govuk-label govuk-visually-hidden">Filter </label>
                <input name="tna-multi-select-search" id="input-filter" class="tna-multi-select-search__filter-input govuk-input" type="text" aria-describedby="fieldId-filter-count" placeholder="Filter @fieldName">
            </div>
            <div class="js-selected-count"></div>
            <span id="fieldId-filter-count" class="govuk-visually-hidden js-filter-count" aria-live="polite"></span>
            <div class="tna-multi-select-search__list-container js-container">
                <ul class="govuk-checkboxes tna-multi-select-search__list"
                id="fieldId" aria-describedby="fieldId-filter-count">
                        <li class="govuk-checkboxes__item">
                            <input class="govuk-checkboxes__input" id="formFieldId-index" name="formFieldId" type="checkbox" value="value">
                            <label class="govuk-label govuk-checkboxes__label" for="formFieldId-index">name</label>
                        </li>
                </ul>
            </div>
        </div>
    </div>
    `

  await renderModules()

  expect(document.body.innerHTML).toContain('<div class="tna-multi-select-search" data-module="multi-select-search" data-module-active="true">')
})
