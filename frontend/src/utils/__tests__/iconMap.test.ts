import ICON_MAP, { getIconEmoji } from '../iconMap';

describe('iconMap', () => {
  it('exports ICON_MAP with known keys', () => {
    expect(ICON_MAP.wallet).toBe('👛');
    expect(ICON_MAP.home).toBe('🏠');
    expect(ICON_MAP.car).toBe('🚗');
    expect(ICON_MAP.squirrel).toBe('🐿️');
  });

  it('getIconEmoji returns emoji for valid key', () => {
    expect(getIconEmoji('wallet')).toBe('👛');
    expect(getIconEmoji('home')).toBe('🏠');
  });

  it('getIconEmoji returns default folder emoji when key is undefined', () => {
    expect(getIconEmoji()).toBe('📁');
    expect(getIconEmoji(undefined)).toBe('📁');
  });

  it('getIconEmoji returns fallback when key is unknown', () => {
    expect(getIconEmoji('nonexistent', '❓')).toBe('❓');
  });

  it('getIconEmoji returns default when key is unknown and no fallback', () => {
    expect(getIconEmoji('nonexistent')).toBe('📁');
  });

  it('getIconEmoji returns custom fallback when key is undefined', () => {
    expect(getIconEmoji(undefined, '🔍')).toBe('🔍');
  });
});
