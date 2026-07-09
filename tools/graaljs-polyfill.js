// Minimal TextEncoder/TextDecoder (UTF-8) polyfill for GraalJS, which does
// not provide the WHATWG Encoding API that carve-js relies on. Prepended to
// the bundled renderer by tools/build-carve-bundle.sh.
(function (g) {
  if (typeof g.TextEncoder === 'undefined') {
    g.TextEncoder = function TextEncoder() {};
    g.TextEncoder.prototype.encode = function (str) {
      var out = [];
      for (var i = 0; i < str.length; i++) {
        var c = str.codePointAt(i);
        if (c > 0xffff) i++;
        if (c < 0x80) {
          out.push(c);
        } else if (c < 0x800) {
          out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
        } else if (c < 0x10000) {
          out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
        } else {
          out.push(
            0xf0 | (c >> 18),
            0x80 | ((c >> 12) & 0x3f),
            0x80 | ((c >> 6) & 0x3f),
            0x80 | (c & 0x3f),
          );
        }
      }
      return new Uint8Array(out);
    };
  }
  if (typeof g.TextDecoder === 'undefined') {
    g.TextDecoder = function TextDecoder() {};
    g.TextDecoder.prototype.decode = function (bytes) {
      var arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
      var out = '';
      var i = 0;
      while (i < arr.length) {
        var b = arr[i];
        var c;
        if (b < 0x80) {
          c = b;
          i += 1;
        } else if (b < 0xe0) {
          c = ((b & 0x1f) << 6) | (arr[i + 1] & 0x3f);
          i += 2;
        } else if (b < 0xf0) {
          c = ((b & 0x0f) << 12) | ((arr[i + 1] & 0x3f) << 6) | (arr[i + 2] & 0x3f);
          i += 3;
        } else {
          c =
            ((b & 0x07) << 18) |
            ((arr[i + 1] & 0x3f) << 12) |
            ((arr[i + 2] & 0x3f) << 6) |
            (arr[i + 3] & 0x3f);
          i += 4;
        }
        out += String.fromCodePoint(c);
      }
      return out;
    };
  }
})(typeof globalThis !== 'undefined' ? globalThis : this);
