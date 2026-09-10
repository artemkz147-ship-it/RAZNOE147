from pathlib import Path
import base64

root=Path('InkFlowReader')
res=root/'app/src/main/res/drawable'
patch=Path('inkflow_patch_167')

# Replace the old vector/text-like placeholders with real raster artwork.
assets={
    'book_min.b64':'cover_placeholder_book.webp',
    'comic_min.b64':'cover_placeholder_comic.webp',
    'document_min.b64':'cover_placeholder_document.webp',
    'archive_min.b64':'cover_placeholder_archive.webp',
}
for old in ['cover_placeholder_book.xml','cover_placeholder_comic.xml','cover_placeholder_document.xml','cover_placeholder_archive.xml']:
    (res/old).unlink(missing_ok=True)

for src_name,out_name in assets.items():
    raw=base64.b64decode((patch/src_name).read_text().strip(), validate=True)
    if len(raw)<1500 or raw[:4]!=b'RIFF' or raw[8:12]!=b'WEBP':
        raise SystemExit(f'invalid WebP asset: {src_name}')
    (res/out_name).write_bytes(raw)
    print(out_name, len(raw))

build=root/'app/build.gradle'
s=build.read_text()
s=s.replace('versionCode 21','versionCode 22')
s=s.replace("versionName '1.6.6'","versionName '1.6.7'")
if "versionName '1.6.7'" not in s:
    raise SystemExit('1.6.7 version bump failed')
build.write_text(s)
