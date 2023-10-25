/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      transitionProperty: {
        width: "width",
      },
    },
    fontFamily: {
      sans: ["Inter", "sans-serif"],
      serif: ["Manrope", "serif"],
      mono: ["Roboto Mono", "monospace"],
    },
  },
  darkMode: "class",
  plugins: [
    require("tailwind-scrollbar")({ nocompatible: true }),
    require("@tailwindcss/line-clamp"),
  ],
};
