// Test-only preload. No production endpoints or credentials are allowed through.
const realFetch = globalThis.fetch;
const destination = new URL(process.env.FACTORIO_TEST_PORTAL);
if (destination.hostname !== '127.0.0.1') throw new Error('Test portal must be loopback');
globalThis.fetch = (input, options) => {
  const url = new URL(String(input));
  if (url.hostname !== 'mods.factorio.com' && url.origin !== destination.origin) {
    throw new Error(`Network blocked by publishing test: ${url.origin}`);
  }
  return realFetch(new URL(url.pathname + url.search, destination), options);
};
