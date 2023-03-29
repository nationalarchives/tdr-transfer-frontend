import { Disclosure } from "../src/viewtransfers/disclosure"

describe("Disclosure", () => {
  let button: Element
  let controlledNode: HTMLElement
  let disclosure: Disclosure

  beforeEach(() => {
    button = document.createElement("button")
    button.setAttribute("aria-controls", "controlled-node")
    document.body.appendChild(button)

    controlledNode = document.createElement("div")
    controlledNode.id = "controlled-node"
    document.body.appendChild(controlledNode)

    disclosure = new Disclosure(button)
  })

  afterEach(() => {
    document.body.removeChild(button)
    document.body.removeChild(controlledNode)
  })

  describe("constructor", () => {
    test("should set button and controlledNode properties", () => {
      expect(disclosure["button"]).toBe(button)
      expect(disclosure["controlledNode"]).toBe(controlledNode)
    })

    test("should hide the controlled node", () => {
      expect(controlledNode.getAttribute("hidden")).toBe("")
    })
  })

  describe("show", () => {
    test("should remove hidden attribute from controlled node", () => {
      controlledNode.setAttribute("hidden", "")
      disclosure.show()
      expect(controlledNode.getAttribute("hidden")).toBeNull()
    })
  })

  describe("toggle", () => {
    test("should set aria-expanded attribute to true when it is false", () => {
      button.setAttribute("aria-expanded", "false")
      disclosure.toggle()
      expect(button.getAttribute("aria-expanded")).toBe("true")
    })

    test("should set aria-expanded attribute to false when it is true", () => {
      button.setAttribute("aria-expanded", "true")
      disclosure.toggle()
      expect(button.getAttribute("aria-expanded")).toBe("false")
    })

    test("should call hide method when aria-expanded is true", () => {
      button.setAttribute("aria-expanded", "true")
      const spy = jest.spyOn(disclosure, "hide")
      disclosure.toggle()
      expect(spy).toHaveBeenCalled()
    })

    test("should call show method when aria-expanded is false", () => {
      button.setAttribute("aria-expanded", "false")
      const spy = jest.spyOn(disclosure, "show")
      disclosure.toggle()
      expect(spy).toHaveBeenCalled()
    })
  })
})
