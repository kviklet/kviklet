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
  plugins: [require("tailwind-scrollbar")({ nocompatible: true })],
};
