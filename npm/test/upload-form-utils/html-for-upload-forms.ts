export const htmlForFolderUploadForm = `
      <div id="file-upload" class="govuk-grid-row">
          <div class="govuk-grid-column-two-thirds">
              <input name="csrfToken" value="abcde">
              <form id="file-upload-form" data-consignment-id="ee948bcd-ebe3-4dfd-8928-2b2c9c586b40">
                  <div class="govuk-form-group">
                      <div class="drag-and-drop">
                            <div class="govuk-form-group">
                                <div id="selection-area">
                                    <div id="multiple-folder-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="multiple-folder-selected-message-text">
                                        <p id="multiple-folder-selected-message-text" class="govuk-error-message">
                                            <span class="govuk-visually-hidden">Error:</span> You can only upload one top-level folder per consignment. However, that folder can contain multiple files and sub folders.
                                        </p>
                                    </div>
                                    <div id="multiple-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="multiple-object-type-selected-message-text">
                                        <p id="multiple-object-type-selected-message-text" class="govuk-error-message">
                                            <span class="govuk-visually-hidden">Error:</span> You can not upload single files. Please add your files to a folder and try uploading again.
                                        </p>
                                    </div>
                                    <div id="item-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="wrong-object-type-selected-message-text">
                                        <p id="wrong-object-type-selected-message-text" class="govuk-error-message">
                                            <span class="govuk-visually-hidden">Error:</span> You can only drop a single folder.
                                        </p>
                                    </div>
                                    <div id="nothing-selected-submission-message" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="submission-without-anything-selected-text">
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
                                <div id="success-and-removal-message-container" class="govuk-summary-list govuk-file-upload govuk-visually-hidden">
                                    <div class="js-drag-and-drop-selected drag-and-drop__selected" id="item-selection-success-container">
                                        <p id="success-message-text" aria-live="assertive" aria-atomic="true" class="govuk-!-margin-bottom-3 govuk-!-margin-top-0 drag-and-drop__selected__description">The folder <strong id="files-selected-folder-name" class="folder-name"></strong> (containing <span class="folder-size"></span>) has been selected.</p>
                                        <a id="remove-file-btn" href="#" aria-describedby="files-selected-folder-name" class="govuk-link govuk-link--no-visited-state govuk-!-font-size-19 govuk-body govuk-!-font-weight-bold">Remove<span class="govuk-visually-hidden">&nbsp; selected files</span></a>
                                    </div>
                                    <div class="js-drag-and-drop-selected drag-and-drop__selected" id="removed-selection-container" hidden="true">
                                        <p id="removed-selection-message-text" class="govuk-error-message">The folder "<span class="folder-name"></span>" (containing <span class="folder-size"></span>) has been removed. Select a folder.</p>
                                    </div>
                                </div>
                                <p class="govuk-body">For more information on what metadata will be captured during the upload please visit our FAQ’s page</p>
                                <div class="govuk-form-group">
                                    <fieldset class="govuk-fieldset" aria-describedby="waste-hint">
                                        <div class="govuk-checkboxes">
                                            <div class="govuk-checkboxes__item">
                                                <input class="govuk-checkboxes__input" id="includeTopLevelFolder" name="includeTopLevelFolder" type="checkbox" value="mines">
                                                <label class="govuk-label govuk-checkboxes__label" for="includeTopLevelFolder">
                                                    Check the box if you want to display the name of your top-level folder in the public catalogue.
                                                </label>
                                            </div>
                                        </div>
                                    </fieldset>
                                </div>
                                <div class="govuk-button-group">
                                    <button id="start-upload-button" class="govuk-button" type="submit" data-module="govuk-button" role="button">
                                        Start upload
                                    </button>

                                    <a class="govuk-link" href="/homepage">Cancel</a>
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
          <input name="csrfToken" value="abcde">
          <form id="file-upload-form" data-consignment-id="ee948bcd-ebe3-4dfd-8928-2b2c9c586b40">
              <div class="govuk-form-group">
                  <div class="drag-and-drop">
                      <div id="success-and-removal-message-container" class="js-drag-and-drop-selected drag-and-drop__selected govuk-visually-hidden">
                          <div class="govuk-summary-list__row">
                              <dd class="govuk-summary-list__value drag-and-drop__success"
                                  tabindex="-1" role="alert" aria-describedby="success-message-text">
                                  <div class="success-message-flexbox-container" id="item-selection-success-container">
                                      <p id="success-message-text" class="success-message">The file "<span class="file-name"></span>" has been selected </p>
                                      <a class="success-message-flexbox-item" id="remove-file-btn" href="#">Remove</a>
                                  </div>
                                  <div class="success-message-flexbox-container" id="removed-selection-container" hidden="true">
                                      <p id="removed-selection-message-text" class="govuk-error-message">The file "<span class="file-name"></span>" has been removed. Select a file.</p>
                                  </div>
                              </dd>
                          </div>
                      </div>
                      <div>
                          <div class="govuk-form-group">
                              <div id="selection-area">
                                  <div id="incorrect-file-extension" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="non-word-doc-selected-message-text">
                                      <p id="non-word-doc-selected-message-text" class="govuk-error-message">
                                          <span class="govuk-visually-hidden">Error:</span> You must upload your judgment as a Microsoft Word file (.docx)
                                      </p>
                                  </div>
                                  <div id="multiple-folder-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="multiple-folder-selected-message-text">
                                          <p id="multiple-folder-selected-message-text" class="govuk-error-message">
                                              <span class="govuk-visually-hidden">Error:</span> You can only upload one top-level folder per consignment. However, that folder can contain multiple files and sub folders.
                                          </p>
                                  </div>
                                  <div id="multiple-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="multiple-object-type-selected-message-text">
                                      <p id="multiple-object-type-selected-message-text" class="govuk-error-message">
                                        <span class="govuk-visually-hidden">Error:</span> You must upload a single file
                                      </p>
                                  </div>
                                  <div id="item-selection-failure" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="wrong-object-type-selected-message-text">
                                      <p id="wrong-object-type-selected-message-text" class="govuk-error-message">
                                          <span class="govuk-visually-hidden">Error:</span> You must upload a file
                                      </p>
                                  </div>
                                  <div id="nothing-selected-submission-message" class="govuk-form-group govuk-form-group--error error-messages" hidden="true" tabindex="-1" role="alert" aria-describedby="submission-without-anything-selected-text">
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
                              <div class="govuk-form-group">
                                  <fieldset class="govuk-fieldset" aria-describedby="waste-hint">
                                      <div class="govuk-checkboxes">
                                          <div class="govuk-checkboxes__item">
                                              <input class="govuk-checkboxes__input" id="includeTopLevelFolder" name="includeTopLevelFolder" type="checkbox" value="mines">
                                              <label class="govuk-label govuk-checkboxes__label" for="includeTopLevelFolder">
                                                  Check the box if you want to display the name of your top-level folder in the public catalogue.
                                              </label>
                                          </div>
                                      </div>
                                  </fieldset>
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
