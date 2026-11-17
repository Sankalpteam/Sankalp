const TOTP = (() => {
  const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

  function base32ToBytes(input) {
    const clean = String(input || '')
      .replace(/[\s=-]/g, '')
      .toUpperCase();
    let bits = '';
    const bytes = [];

    for (const ch of clean) {
      const val = BASE32.indexOf(ch);

      if (val < 0) {
        throw new Error('Invalid Base32 secret');
      }

      bits += val.toString(2).padStart(5, '0');
    }

    for (let i = 0; i + 8 <= bits.length; i += 8) {
      bytes.push(parseInt(bits.slice(i, i + 8), 2));
    }

    return new Uint8Array(bytes);
  }

  function parseOtpAuth(uri) {
    const url = new URL(uri);

    if (url.protocol !== 'otpauth:' || url.host !== 'totp') {
      throw new Error('Unsupported OTP URI');
    }

    const params = url.searchParams;

    return {
      issuer: params.get('issuer') || '',
      secret: params.get('secret') || '',
      digits: Number(params.get('digits') || 6),
      period: Number(params.get('period') || 30),
      algorithm: (params.get('algorithm') || 'SHA1').toUpperCase(),
      label: decodeURIComponent(url.pathname.replace(/^\//, ''))
    };
  }

  async function generate(
    secret,
    counter,
    digits = 6,
    algorithm = 'SHA1'
  ) {
    const keyBytes = base32ToBytes(secret);
    const buffer = new ArrayBuffer(8);
    const view = new DataView(buffer);

    view.setUint32(4, Number(counter), false);

    const hashName =
      algorithm === 'SHA256'
        ? 'SHA-256'
        : algorithm === 'SHA512'
          ? 'SHA-512'
          : 'SHA-1';

    const key = await crypto.subtle.importKey(
      'raw',
      keyBytes,
      {
        name: 'HMAC',
        hash: {
          name: hashName
        }
      },
      false,
      ['sign']
    );

    const mac = new Uint8Array(
      await crypto.subtle.sign('HMAC', key, buffer)
    );
    const offset = mac[mac.length - 1] & 15;
    const binary =
      ((mac[offset] & 127) << 24) |
      (mac[offset + 1] << 16) |
      (mac[offset + 2] << 8) |
      mac[offset + 3];

    return String(binary % (10 ** digits)).padStart(
      digits,
      '0'
    );
  }

  return {
    generate,
    parseOtpAuth,
    base32ToBytes
  };
})();

window.TOTP = TOTP;
