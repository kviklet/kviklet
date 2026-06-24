import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

// Mock ResizeObserver for headlessui components
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Mock matchMedia for auto-animate, which reads prefers-reduced-motion.
window.matchMedia = (query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
});

// jsdom lacks the Web Animations API that auto-animate drives. Stub the bits it
// touches (new KeyframeEffect / new Animation -> play/cancel/finish) as no-ops;
// the tests don't assert on animations.
globalThis.KeyframeEffect =
  class KeyframeEffect {} as unknown as typeof KeyframeEffect;

globalThis.Animation = class Animation {
  finished = Promise.resolve();
  play() {}
  cancel() {}
  addEventListener() {}
  removeEventListener() {}
} as unknown as typeof Animation;

afterEach(() => {
  cleanup();
});
