#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SPEC_DIR="$ROOT_DIR/openapi"
OUT_DIR="$SCRIPT_DIR/dist"
VERSION="${SWAGGER_UI_VERSION:-latest}"

echo "==> Vendoring swagger-ui-dist@$VERSION"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
npm install --silent --no-save --prefix "$TMP_DIR" "swagger-ui-dist@$VERSION"

echo "==> Preparing $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/specs"
cp -R "$TMP_DIR/node_modules/swagger-ui-dist/." "$OUT_DIR/"
cp "$SPEC_DIR/organization-service.yaml" "$OUT_DIR/specs/"
cp "$SPEC_DIR/orgdirectory-service.yaml" "$OUT_DIR/specs/"

echo "==> Writing index.html"
cat > "$OUT_DIR/index.html" <<'HTML'
<!DOCTYPE html>
<html lang="ru">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Organization API — Swagger UI</title>
    <link rel="stylesheet" href="./swagger-ui.css" />
    <link rel="icon" type="image/png" href="./favicon-32x32.png" sizes="32x32" />
    <link rel="icon" type="image/png" href="./favicon-16x16.png" sizes="16x16" />
  </head>
  <body>
    <div id="swagger-ui"></div>
    <script src="./swagger-ui-bundle.js"></script>
    <script src="./swagger-ui-standalone-preset.js"></script>
    <script>
      window.onload = function () {
        window.ui = SwaggerUIBundle({
          urls: [
            {
              url: "./specs/organization-service.yaml",
              name: "Organization Service",
            },
            {
              url: "./specs/orgdirectory-service.yaml",
              name: "OrgDirectory Service",
            },
          ],
          "urls.primaryName": "Organization Service",
          dom_id: "#swagger-ui",
          presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset,
          ],
          layout: "StandaloneLayout",
          deepLinking: true,
        });
      };
    </script>
  </body>
</html>
HTML

echo "==> Done"
echo "Static documentation: $OUT_DIR"
