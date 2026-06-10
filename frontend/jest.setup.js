jest.mock('@react-native-async-storage/async-storage', () => ({
  setItem: jest.fn(() => Promise.resolve()),
  getItem: jest.fn(() => Promise.resolve(null)),
  removeItem: jest.fn(() => Promise.resolve()),
  multiRemove: jest.fn(() => Promise.resolve()),
}));

// useToast is consumed by most screens. Stub it globally so component/screen
// tests don't need to wrap everything in a ToastProvider. ToastProvider itself
// is kept real (requireActual); the provider's own logic is exercised by its
// dedicated unit test, which unmocks this module.
globalThis.__mockShowToast = jest.fn();
globalThis.__mockHideToast = jest.fn();
jest.mock('./src/contexts/ToastContext', () => {
  const actual = jest.requireActual('./src/contexts/ToastContext');
  return {
    __esModule: true,
    ...actual,
    useToast: () => ({
      toasts: [],
      showToast: globalThis.__mockShowToast,
      hideToast: globalThis.__mockHideToast,
    }),
  };
});
