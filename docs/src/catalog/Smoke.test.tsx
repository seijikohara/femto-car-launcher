// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import Smoke from "./Smoke";

it("renders a React component under jsdom", () => {
    render(<Smoke label="hello" />);
    expect(screen.getByTestId("smoke").textContent).toBe("hello");
});
