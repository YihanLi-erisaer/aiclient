;(function (config) {
    config.resolve = config.resolve || {}
    config.resolve.fallback = Object.assign({}, config.resolve.fallback || {}, {
        fs: false,
        path: require.resolve('path-browserify'),
        crypto: false,
    })
})(config)
