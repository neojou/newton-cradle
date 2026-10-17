// Merged into the Kotlin/JS webpack config.
// Emscripten PhysX references Node modules that the browser build does not need.
config.resolve = {
    fallback: {
        path: false,
        fs: false
    }
}
