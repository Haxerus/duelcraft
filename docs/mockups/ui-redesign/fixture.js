// Shared fictional review content. No game connection, persistence or rules engine.
window.DC = {
  cards: [
    [89631139, 'Blue-Eyes White Dragon'], [483, 'Parallel Teleport'],
    [2511, 'Labrynth Cooclock'], [10000, 'Ten Thousand Dragon'],
    [27551, 'Limit Reverse'], [32864, 'The 13th Grave'],
    [35699, 'SPYRAL Sleeper'], [39015, 'Assault Sentinel'],
    [41546, 'D/D Savant Thomas'], [41777, 'Gem-Enhancement'],
    [44818, 'Starry Knight Orbitael'], [50755, "Magician's Circle"],
    [56889, 'Cross-Dimensional Duel'], [59080, 'Magia Magic - Thunder of Judgment'],
    [62121, 'Castle of Dark Illusions']
  ].map(([code, name]) => ({code, name, image:`assets/${code}.png`})),
  art: '../featured-art/blue-eyes.png',
  back: 'assets/card-back.png',
  deckName: 'Dragon study',
  players: ['Alex', 'Rowan', 'Mira'],
  log: ['Alex summoned Blue-Eyes White Dragon.', 'You drew a card.', 'Your turn began.'],
};
DC.main = [DC.cards[0], ...DC.cards.slice(1,14).flatMap(card => [card,card,card])];
DC.image = (card, cls='') => `<img class="${cls}" src="${card.image}" alt="${card.name}" draggable="false">`;
DC.fit = function () {
  const frame = document.getElementById('frame');
  const scale = Math.min(innerWidth / 1280, innerHeight / 720);
  frame.style.transform = `scale(${scale})`;
  frame.style.left = `${Math.max(0, (innerWidth - 1280 * scale) / 2)}px`;
  frame.style.top = `${Math.max(0, (innerHeight - 720 * scale) / 2)}px`;
};
addEventListener('resize', DC.fit);
