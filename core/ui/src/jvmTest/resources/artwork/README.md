Synthetic 80 x 40 artwork fixtures, generated for decoder regression tests.
All static images are solid #cc2233. Animated WebP starts with that red frame
and alternates with #2244cc; decoding must display the first frame consistently.

Generated with ImageMagick (`magick`) and libwebp (`cwebp`):

```sh
magick -size 80x40 xc:'#cc2233' cover.png
magick cover.png -quality 90 cover.jpg
cwebp -quiet -lossless cover.png -o cover-lossless.webp
cwebp -quiet -q 85 cover.png -o cover-lossy.webp
magick -delay 10 -size 80x40 xc:'#cc2233' -size 80x40 xc:'#2244cc' -loop 0 cover-animated.webp
```
