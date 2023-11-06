// eslint-disable-next-line @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || ":8080";
const baseUrl = `${window.location.protocol}//${window.location.hostname}${apiBasePath}`;

export default baseUrl;
