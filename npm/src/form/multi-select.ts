import AriaAutocomplete from "aria-autocomplete"

export const displayMultiSelect = (
  selector: string,
  placeholder: string
): void => {
  AriaAutocomplete(document.querySelector(selector)!, {
    placeholder: placeholder,
    deleteOnBackspace: true,
    showAllControl: true,
    autoGrow: false,
    srAssistiveText: "",
    cssNameSpace: "govuk-multi-autocomplete"
  })
}
