config.devServer = config.devServer || {};
config.devServer.proxy = config.devServer.proxy || [];

config.devServer.proxy.push({
  context: ["/api"],
  target: "http://192.168.100.183:8080",
  changeOrigin: true,
  secure: false,
});
