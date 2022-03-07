export const htmlForFolderUploadForm = `
      <div id="file-upload" class="govuk-grid-row">
          <div class="govuk-grid-column-two-thirds">
              <form id="file-upload-form" data-consignment-id="ee948bcd-ebe3-4dfd-8928-2b2c9c586b40">
                  <div class="govuk-form-group">
                      <div class="drag-and-drop">
                          <div class="govuk-summary-list govuk-file-upload">
                              <div class="govuk-summary-list__row">
                                  <dd id="folder-selection-success" class="govuk-summary-list__value drag-and-drop__success" hidden=""
                                      tabindex="-1" role="alert" aria-describedby="success-message-text">
                                      <div>
                                           <p id="success-message-text">The folder "<span id="folder-name"></span>" (containing <span id="folder-size"></span>) has been selected</p>
                                      </div>
                                 </dd>
                              </div>
                          </div>
                          <div>
                              <div class="govuk-form-group">
                                  <div id="selection-area">
                                      <div id="multiple-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="multiple-object-type-selected-message-text">
                                          <p id="multiple-object-type-selected-message-text" class="govuk-error-message">
                                              <span class="govuk-visually-hidden">Error:</span> You must upload a single folder
                                          </p>
                                      </div>
                                      <div id="item-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="wrong-object-type-selected-message-text">
                                          <p id="wrong-object-type-selected-message-text" class="govuk-error-message">
                                              <span class="govuk-visually-hidden">Error:</span> You can only drop a single folder
                                          </p>
                                      </div>
                                      <div id="nothing-selected-submission-message" class="govuk-form-group govuk-form-group--error error-messages" tabindex="-1" role="alert" aria-describedby="submission-without-anything-selected-text">
                                          <p id="submission-without-anything-selected-text" class="govuk-error-message">
                                              <span class="govuk-visually-hidden">Error:</span> Select a folder to upload.
                                          </p>
                                      </div>
                                      <div class="drag-and-drop__dropzone">
                                          <input type="file" id="file-selection" name="files"
                                              class="govuk-file-upload drag-and-drop__input" webkitdirectory
                                              accept="image/jpeg" aria-hidden="true"
                                          >
                                          <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single folder here or</p>
                                          <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
                                          Choose folder
                                          </label>
                                      </div>
                                  </div>
                                  <p class="govuk-body">For more information on what metadata will be captured during the upload please visit our FAQ’s page</p>

                                  <div class="govuk-button-group">
                                      <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                          Start upload
                                      </button>

                                      <a class="govuk-link" href="/homepage">Cancel</a>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>
              </form>
          </div>
      </div>
      <div id="upload-progress" class="govuk-grid-row" hidden></div>`

export const htmlForFileUploadForm = `
  <div id="file-upload" class="govuk-grid-row">
      <div class="govuk-grid-column-two-thirds">
          <form id="file-upload-form" data-consignment-id="ee948bcd-ebe3-4dfd-8928-2b2c9c586b40">
              <div class="govuk-form-group">
                  <div class="drag-and-drop">
                      <div class="govuk-summary-list govuk-file-upload">
                          <div class="govuk-summary-list__row">
                              <dd id="folder-selection-success" class="govuk-summary-list__value drag-and-drop__success" hidden
                                  tabindex="-1" role="alert" aria-describedby="success-message-text">
                                  <div>
                                      <p id="success-message-text">The file "<span id="file-name"></span>" has been selected</p>
                                  </div>
                              </dd>
                          </div>
                      </div>
                      <div>
                          <div class="govuk-form-group">
                              <div id="selection-area">
                                  <div id="incorrect-file-extension" class="govuk-form-group govuk-form-group--error error-messages" hidden="" tabindex="-1" role="alert" aria-describedby="non-word-doc-selected-message-text">
                                      <p id="non-word-doc-selected-message-text" class="govuk-error-message">
                                          <span class="govuk-visually-hidden">Error:</span> You must upload your judgment as a Microsoft Word file (.docx)
                                      </p>
                                  </div>
                                  <div id="multiple-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="" tabindex="-1" role="alert" aria-describedby="multiple-object-type-selected-message-text">
                                      <p id="multiple-object-type-selected-message-text" class="govuk-error-message">
                                        <span class="govuk-visually-hidden">Error:</span> You must upload a single file
                                      </p>
                                  </div>
                                  <div id="item-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="" tabindex="-1" role="alert" aria-describedby="wrong-object-type-selected-message-text">
                                      <p id="wrong-object-type-selected-message-text" class="govuk-error-message">
                                          <span class="govuk-visually-hidden">Error:</span> You must upload a file
                                      </p>
                                  </div>
                                  <div id="nothing-selected-submission-message" class="govuk-form-group govuk-form-group--error error-messages" hidden="" tabindex="-1" role="alert" aria-describedby="submission-without-anything-selected-text">
                                      <p id="submission-without-anything-selected-text" class="govuk-error-message">
                                        <span class="govuk-visually-hidden">Error:</span> You did not select a file for upload.
                                      </p>
                                  </div>
                                  <div class="drag-and-drop__dropzone">
                                      <input type="file" id="file-selection" name="files"
                                          class="govuk-file-upload drag-and-drop__input" multiple
                                      >
                                      <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single file here or</p>
                                      <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
                                          Choose file
                                      </label>
                                  </div>
                              </div>

                              <div class="govuk-button-group">
                                  <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                      Start upload
                                  </button>
  
                                  <a class="govuk-link" href="/">Cancel</a>
                              </div>
                          </div>
                      </div>
                  </div>
              </div>
          </form>
          <!--        Form to redirect user once upload has completed. It sends consignmentId to record processing placeholder page -->
          @form(routes.FileChecksController.fileChecksPage(consignmentId), Symbol("id") -> "upload-data-form") { }
      </div>
  </div>
  <div id="upload-progress" class="govuk-grid-row" hidden>`
