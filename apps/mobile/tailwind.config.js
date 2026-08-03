/**
 * Brand tokens sourced verbatim from brand/brand-colors.md's own "Tailwind theme
 * (drop-in)" section — the same canonical values apps/web's globals.css derives its
 * CSS custom properties from. See project-docs/prompts/30-expo-mobile-app.md's
 * "Brand parity via shared design tokens" deliverable.
 *
 * @type {import('tailwindcss').Config}
 */
module.exports = {
  content: ["./app/**/*.{js,jsx,ts,tsx}", "./components/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        brand: "#0B5FD0",
        "brand-strong": "#003488",
        sky: "#38BDF8",
        sun: "#F4B740",
        ink: "#0F1B2D",
        "ink-muted": "#5B6B7F",
        bg: "#F5F8FC",
        surface: "#FFFFFF",
        line: "#E1E8F0",
        success: "#2E9E6B",
        warning: "#D9902B",
        danger: "#D24545",
      },
    },
  },
  plugins: [],
};
