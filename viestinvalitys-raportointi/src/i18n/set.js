export function set(object, path, value) {
  // Optional string-path support.
  // You can remove this `if` block if you don't need it.
  if (typeof path === 'string') {
    const isQuoted = (str) => str[0] === '"' && str.at(-1) === '"';
    path = path
      .split(/[.[\]]+/)
      .filter((x) => x)
      .map((x) => (!isNaN(Number(x)) ? Number(x) : x))
      .map((x) => (typeof x === 'string' && isQuoted(x) ? x.slice(1, -1) : x));
  }

  if (path.length === 0) {
    throw new Error('The path must have at least one entry in it');
  }

  const [head, ...tail] = path;

  if (tail.length === 0) {
    object[head] = value;
    return object;
  }

  if (!(head in object)) {
    object[head] = typeof tail[0] === 'number' ? [] : {};
  }

  set(object[head], tail, value);
  return object;
}
