// Map the source rectangle to a quadrilateral while keeping straight lines straight.
// Points run clockwise from the top-left and are relative to the source origin.
export function perspectiveMatrix(points, width, height) {
  const [[x0, y0], [x1, y1], [x2, y2], [x3, y3]] = points;
  const dx1 = x1 - x2;
  const dx2 = x3 - x2;
  const dy1 = y1 - y2;
  const dy2 = y3 - y2;
  const dx = x0 - x1 + x2 - x3;
  const dy = y0 - y1 + y2 - y3;
  const determinant = dx1 * dy2 - dx2 * dy1;

  if (Math.abs(determinant) < 1e-8) return null;

  const g = (dx * dy2 - dx2 * dy) / determinant;
  const h = (dx1 * dy - dx * dy1) / determinant;

  return [
    (x1 - x0 + g * x1) / width, (y1 - y0 + g * y1) / width, 0, g / width,
    (x3 - x0 + h * x3) / height, (y3 - y0 + h * y3) / height, 0, h / height,
    0, 0, 1, 0,
    x0, y0, 0, 1,
  ];
}

export function isConvex(points) {
  return points.every(([x, y], index) => {
    const [nextX, nextY] = points[(index + 1) % 4];
    const [endX, endY] = points[(index + 2) % 4];
    return (nextX - x) * (endY - nextY) - (nextY - y) * (endX - nextX) > .014;
  });
}

export function fitProjection(wall, projection, points) {
  const { clientWidth: width, clientHeight: height } = wall;
  if (!width || !height) return;
  const left = projection.offsetLeft;
  const top = projection.offsetTop;
  const pixels = points.map(([x, y]) => [x * width - left, y * height - top]);
  const matrix = perspectiveMatrix(pixels, projection.offsetWidth, projection.offsetHeight);
  if (matrix) projection.style.transform = `matrix3d(${matrix.join(',')})`;
}
