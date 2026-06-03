## Production Response Contract

Base endpoint:

- `POST /api/gang-sheets/pdf`
- `GET /api/gang-sheets/{id}/download`

## Success response

HTTP status: `201 Created`

```json
{
  "success": true,
  "code": "PDF_CREATED",
  "message": "PDF created successfully",
  "data": {
    "id": "f1097d09-cef9-4f37-a057-71992cafe066",
    "fileName": "gang-sheet-f1097d09-cef9-4f37-a057-71992cafe066.pdf",
    "downloadUrl": "/api/gang-sheets/f1097d09-cef9-4f37-a057-71992cafe066/download",
    "itemCount": 4,
    "sheet": {
      "width": 22.0,
      "height": 23.25,
      "unit": "INCH"
    }
  }
}
```

If the generated sheet has print-quality warnings, `warnings` is included inside `data`:

```json
{
  "success": true,
  "code": "PDF_CREATED",
  "message": "PDF created successfully",
  "data": {
    "id": "f1097d09-cef9-4f37-a057-71992cafe066",
    "fileName": "gang-sheet-f1097d09-cef9-4f37-a057-71992cafe066.pdf",
    "downloadUrl": "/api/gang-sheets/f1097d09-cef9-4f37-a057-71992cafe066/download",
    "itemCount": 4,
    "sheet": {
      "width": 22.0,
      "height": 23.25,
      "unit": "INCH"
    },
    "warnings": [
      "Item at index 2 has low DPI. actualDpi=118.40, recommendedDpi>=150"
    ]
  }
}
```

## Validation error

HTTP status: `400 Bad Request`

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "[0].img",
      "message": "img must not be blank"
    },
    {
      "field": "[0].width",
      "message": "width must be greater than 0"
    }
  ]
}
```

## Business error

HTTP status: `400 Bad Request`

```json
{
  "success": false,
  "code": "IMAGE_LOAD_ERROR",
  "message": "Cannot load image",
  "errors": [
    {
      "message": "Cannot load image from source: https://example.com/image.png"
    }
  ]
}
```

## Not found

HTTP status: `404 Not Found`

```json
{
  "success": false,
  "code": "PDF_NOT_FOUND",
  "message": "PDF not found"
}
```

## Internal error

HTTP status: `500 Internal Server Error`

```json
{
  "success": false,
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Unexpected error"
}
```

## Notes

- `status` is not returned in the response body. HTTP status already expresses request state.
- `timestamp` is not returned.
- `errors` only appears on error responses.
- `warnings` only appears when the PDF is created successfully and there are print warnings.
- `downloadUrl` uses the generated PDF `id`, not the raw file path on disk.
