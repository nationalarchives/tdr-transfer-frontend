export class Disclosure {
  private readonly button: Element
  private readonly controlledNode?: HTMLElement

  constructor(button: Element) {
    this.button = button
    const id: string | undefined = this.button.getAttribute("aria-controls") as
      | string
      | undefined

    if (!id) {
      return
    }

    this.controlledNode = document.getElementById(id)!
    this.hide()
    this.button.addEventListener("click", this.toggle)
  }

  hide: () => void = () => {
    if (this.controlledNode) this.controlledNode.setAttribute("hidden", "")
  }

  show: () => void = () => {
    if (this.controlledNode) this.controlledNode.removeAttribute("hidden")
  }

  toggle: () => void = () => {
    if (this.button.getAttribute("aria-expanded") === "true") {
      this.button.setAttribute("aria-expanded", "false")
      this.hide()
    } else {
      this.button.setAttribute("aria-expanded", "true")
      this.show()
    }
  }
}