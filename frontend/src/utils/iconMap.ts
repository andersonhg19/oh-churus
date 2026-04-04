const ICON_MAP: Record<string, string> = {
  // Dinero
  wallet: '👛', money: '💰', dollar: '💵', coin: '🪙', bank: '🏦',
  'credit-card': '💳', 'piggy-bank': '🐷', chart: '📈', 'bar-chart': '📊', percent: '💹',
  // Hogar
  home: '🏠', key: '🔑', zap: '⚡', droplet: '💧', flame: '🔥',
  tool: '🔧', bulb: '💡', bed: '🛏️',
  // Comida
  'shopping-cart': '🛒', 'shopping-bag': '🛍️', coffee: '☕', pizza: '🍕',
  apple: '🍎', cooking: '🍳', cake: '🎂', wine: '🍷',
  // Transporte
  car: '🚗', bus: '🚌', truck: '🚛', bike: '🚲', plane: '✈️',
  fuel: '⛽', navigation: '🧭', train: '🚆',
  // Trabajo
  briefcase: '💼', laptop: '💻', desktop: '🖥️', phone: '📱',
  mail: '📧', calendar: '📅', clock: '⏰', 'trending-up': '📈',
  // Salud y Educación
  heart: '❤️', hospital: '🏥', pill: '💊', thermometer: '🌡️',
  activity: '🩺', book: '📚', graduation: '🎓', award: '🏆', bookmark: '📖',
  // Entretenimiento
  film: '🎬', music: '🎵', tv: '📺', gamepad: '🎮', headphones: '🎧',
  party: '🎉', sports: '⚽', gym: '🏋️',
  // Otros
  gift: '🎁', pet: '🐾', baby: '👶', clothes: '👕', scissors: '✂️',
  star: '⭐', shield: '🛡️', squirrel: '🐿️',
};

export const getIconEmoji = (iconKey?: string, fallback?: string): string => {
  if (!iconKey) return fallback || '📁';
  return ICON_MAP[iconKey] || fallback || '📁';
};

export default ICON_MAP;
