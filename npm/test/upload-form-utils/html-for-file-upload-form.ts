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
                                       <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                          <path d="M25,6.2L8.7,23.2L0,14.1l4-4.2l4.7,4.9L21,2L25,6.2z"></path>
                                       </svg>
                                       <p id="success-message-text">The folder "<span id="folder-name"></span>" (containing <span id="folder-size"></span>) has been selected</p>
                                    </div>
                                 </dd>
                                 <dd id="item-selection-failure" class="govuk-summary-list__value drag-and-drop__failure" hidden=""
                                    tabindex="-1" role="alert" aria-describedby="non-folder-selected-message-text">
                                    <div>
                                       <span class="drag-and-drop__error">
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                             <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                          </svg>
                                       </span>
                                       <p id="non-folder-selected-message-text">You can only drop a single folder</p>
                                    </div>
                                 </dd>
                                 <dd id="nothing-selected-submission-message" class="govuk-summary-list__value drag-and-drop__failure" hidden=""
                                      tabindex="-1" role="alert" aria-describedby="submission-without-a-folder-message-text">
                                    <div>
                                       <span class="drag-and-drop__error">
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg" width="25px" height="25px">
                                             <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                          </svg>
                                       </span>
                                       <p id="submission-without-a-folder-message-text">Select a folder to upload.</p>
                                    </div>
                                 </dd>
                              </div>
                          </div>
                          <div>
                              <div class="govuk-form-group">
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
                                  <p class="govuk-body">For more information on what metadata will be captured during the upload please visit our FAQ’s page</p>
                                  <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                      Start upload
                                  </button>
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
                                      <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg">
                                          <path d="M25,6.2L8.7,23.2L0,14.1l4-4.2l4.7,4.9L21,2L25,6.2z"></path>
                                      </svg>
                                      <p id="success-message-text">The file "<span id="file-name"></span>" has been selected</p>
                                  </div>
                              </dd>
                              <dd id="incorrect-file-extension" class="govuk-summary-list__value drag-and-drop__failure" hidden
                                  tabindex="-1" role="alert" aria-describedby="non-file-selected-message-text">
                                  <div>
                                      <span class="drag-and-drop__error">
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg">
                                             <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                          </svg>
                                     </span>
                                      <p id="non-word-doc-selected-message-text">You can only upload a Microsoft Word file. Please try again or contact us</p>
                                  </div>
                              </dd>
                              <dd id="item-selection-failure" class="govuk-summary-list__value drag-and-drop__failure" hidden
                                  tabindex="-1" role="alert" aria-describedby="non-file-selected-message-text">
                                  <div>
                                      <span class="drag-and-drop__error">
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg">
                                             <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                          </svg>
                                     </span>
                                      <p id="non-file-selected-message-text">You can only drop a single file</p>
                                  </div>
                              </dd>
                              <dd id="nothing-selected-submission-message" class="govuk-summary-list__value drag-and-drop__failure" hidden
                                  tabindex="-1" role="alert" aria-describedby="submission-without-a-file-message-text">
                                  <div>
                                      <span class="drag-and-drop__error">
                                          <svg class="alert-status-svg" role="presentation" focusable="false" viewBox="0 0 25 25" xmlns="http://www.w3.org/2000/svg">
                                             <path d="M13.6,15.4h-2.3v-4.5h2.3V15.4z M13.6,19.8h-2.3v-2.2h2.3V19.8z M0,23.2h25L12.5,2L0,23.2z"></path>
                                          </svg>
                                      </span>
                                      <p id="submission-without-a-file-message-text">Select a file to upload.</p>
                                  </div>
                              </dd>
                          </div>
                      </div>
                      <div>
                          <div class="govuk-form-group">
                              <div class="drag-and-drop__dropzone">
                                  <input type="file" id="file-selection" name="files"
                                      class="govuk-file-upload drag-and-drop__input" multiple
                                  >
                                  <p class="govuk-body drag-and-drop__hint-text">Drag and drop a single file here or</p>
                                  <label for="file-selection" class="govuk-button govuk-button--secondary drag-and-drop__button">
                                      Choose file
                                  </label>
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
          @form(routes.FileChecksController.recordProcessingPage(consignmentId), Symbol("id") -> "upload-data-form") { }
      </div>
  </div>
  <div id="upload-progress" class="govuk-grid-row" hidden>`
