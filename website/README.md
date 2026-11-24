# ProjectorConnect website

A static website with the Android download, setup walkthrough, and an interactive tour of the app.

From the repository root, run `python3 -m http.server 8080 --bind 127.0.0.1 --directory website` and open <http://127.0.0.1:8080>.

The `website` directory can be served as-is by any static host. When updating the app version, replace the APK in `assets` and update its download link and version label in `index.html`.
