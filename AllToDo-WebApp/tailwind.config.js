/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#4ADE80', // Fresh summer leaf green (Tailwind green-400 equivalent)
          hover: '#22c55e',   // Slightly darker for hover (green-500)
        }
      }
    },
  },
  plugins: [],
}

