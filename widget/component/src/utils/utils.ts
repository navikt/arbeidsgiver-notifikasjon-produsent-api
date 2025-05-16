export const getLimitedUrl = () => {
  const {origin, pathname } = window.location;
  return `${origin}/${pathname.split('/')[1]}`;
}
