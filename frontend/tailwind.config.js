/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {},
    fontFamily: {
      sans: ["Inter", "sans-serif"],
      serif: ["Manrope", "serif"],
      mono: ["Roboto Mono", "monospace"],
    },
  },
  darkMode: "class",
  plugins: [require("tailwind-scrollbar")({ nocompatible: true })],
};
